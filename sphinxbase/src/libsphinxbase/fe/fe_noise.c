/* -*- c-basic-offset: 4; indent-tabs-mode: nil -*- */
/* ====================================================================
 * Copyright (c) 2013 Carnegie Mellon University.  All rights 
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * This work was supported in part by funding from the Defense Advanced 
 * Research Projects Agency and the National Science Foundation of the 
 * United States of America, and the CMU Sphinx Speech Consortium.
 *
 * THIS SOFTWARE IS PROVIDED BY CARNEGIE MELLON UNIVERSITY ``AS IS'' AND 
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 *
 */

/* This noise removal algorithm is inspired by the following papers
 * Computationally Efficient Speech Enchancement by Spectral Minina Tracking
 * by G. Doblinger
 *
 * Power-Normalized Cepstral Coefficients (PNCC) for Robust Speech Recognition
 * by C. Kim.
 *
 * For the recent research and state of art see papers about IMRCA and
 * A Minimum-Mean-Square-Error Noise Reduction Algorithm On Mel-Frequency
 * Cepstra For Robust Speech Recognition by Dong Yu and others
 */

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#include <math.h>

#include "sphinxbase/prim_type.h"
#include "sphinxbase/ckd_alloc.h"

#include "fe_noise.h"
#include "fe_internal.h"

/* Noise supression constants */
#define SMOOTH_WINDOW 4
#define LAMBDA_POWER 0.7
#define LAMBDA_A 0.999
#define LAMBDA_B 0.5
#define LAMBDA_T 0.85
#define MU_T 0.2
#define MAX_GAIN 20

struct noise_stats_s {
    /* Smoothed power */
    powspec_t *power;
    /* Noise estimate */
    powspec_t *noise;
    /* Signal floor estimate */
    powspec_t *floor;
    /* Peak for temporal masking */
    powspec_t *peak;

    /* Initialize it next time */
    uint8 undefined;
    /* Number of items to process */
    uint32 num_filters;

    /* Precomputed constants */
    powspec_t lambda_power;
    powspec_t comp_lambda_power;
    powspec_t lambda_a;
    powspec_t comp_lambda_a;
    powspec_t lambda_b;
    powspec_t comp_lambda_b;
    powspec_t lambda_t;
    powspec_t mu_t;
    powspec_t max_gain;
    powspec_t inv_max_gain;

    powspec_t smooth_scaling[2 * SMOOTH_WINDOW + 3];
};

static void
fe_low_envelope(noise_stats_t *noise_stats, powspec_t * buf, powspec_t * floor_buf, int32 num_filt)
{
    int i;

    for (i = 0; i < num_filt; i++) {
#ifndef FIXED_POINT
        if (buf[i] >= floor_buf[i]) {
            floor_buf[i] =
                noise_stats->lambda_a * floor_buf[i] + noise_stats->comp_lambda_a * buf[i];
        }
        else {
            floor_buf[i] =
                noise_stats->lambda_b * floor_buf[i] + noise_stats->comp_lambda_b * buf[i];
        }
#else
        if (buf[i] >= floor_buf[i]) {
            floor_buf[i] = fe_log_add(noise_stats->lambda_a + floor_buf[i],
        	                      noise_stats->comp_lambda_a + buf[i]);
        }
        else {
            floor_buf[i] = fe_log_add(noise_stats->lambda_b + floor_buf[i],
        	                      noise_stats->comp_lambda_b + buf[i]);
        }
#endif
    }
}

/* temporal masking */
static void
fe_temp_masking(noise_stats_t *noise_stats, powspec_t * buf, powspec_t * peak, int32 num_filt)
{
    powspec_t cur_in;
    int i;

    for (i = 0; i < num_filt; i++) {
        cur_in = buf[i];

#ifndef FIXED_POINT
        peak[i] *= noise_stats->lambda_t;
        if (buf[i] < noise_stats->lambda_t * peak[i])
            buf[i] = peak[i] * noise_stats->mu_t;
#else
        peak[i] += noise_stats->lambda_t;
        if (buf[i] < noise_stats->lambda_t + peak[i])
            buf[i] = peak[i] + noise_stats->mu_t;
#endif

        if (cur_in > peak[i])
            peak[i] = cur_in;
    }
}

/* spectral weight smoothing */
static void
fe_weight_smooth(noise_stats_t *noise_stats, powspec_t * buf, powspec_t * coefs, int32 num_filt)
{
    int i, j;
    int l1, l2;
    powspec_t coef;

    for (i = 0; i < num_filt; i++) {
        l1 = ((i - SMOOTH_WINDOW) > 0) ? (i - SMOOTH_WINDOW) : 0;
        l2 = ((i + SMOOTH_WINDOW) <
              (num_filt - 1)) ? (i + SMOOTH_WINDOW) : (num_filt - 1);

#ifndef FIXED_POINT
        coef = 0;
        for (j = l1; j <= l2; j++) {
            coef += coefs[j];
        }
        buf[i] = buf[i] * (coef / (l2 - l1 + 1));
#else
        coef = MIN_FIXLOG;
        for (j = l1; j <= l2; j++) {
            coef = fe_log_add(coef, coefs[j]);
        }        
        buf[i] = buf[i] + coef - noise_stats->smooth_scaling[l2 - l1 + 1];
#endif

    }
}

noise_stats_t *
fe_init_noisestats(int num_filters)
{
    int i;
    noise_stats_t *noise_stats;

    noise_stats = (noise_stats_t *) ckd_calloc(1, sizeof(noise_stats_t));

    noise_stats->power =
        (powspec_t *) ckd_calloc(num_filters, sizeof(powspec_t));
    noise_stats->noise =
        (powspec_t *) ckd_calloc(num_filters, sizeof(powspec_t));
    noise_stats->floor =
        (powspec_t *) ckd_calloc(num_filters, sizeof(powspec_t));
    noise_stats->peak =
        (powspec_t *) ckd_calloc(num_filters, sizeof(powspec_t));

    noise_stats->undefined = TRUE;
    noise_stats->num_filters = num_filters;

#ifndef FIXED_POINT
    noise_stats->lambda_power = LAMBDA_POWER;
    noise_stats->comp_lambda_power = 1 - LAMBDA_POWER;
    noise_stats->lambda_a = LAMBDA_A;
    noise_stats->comp_lambda_a = 1 - LAMBDA_A;
    noise_stats->lambda_b = LAMBDA_B;
    noise_stats->comp_lambda_b = 1 - LAMBDA_B;
    noise_stats->lambda_t = LAMBDA_T;
    noise_stats->mu_t = 1 - LAMBDA_T;
    noise_stats->max_gain = MAX_GAIN;
    noise_stats->inv_max_gain = 1.0 / MAX_GAIN;
    
    for (i = 0; i < 2 * SMOOTH_WINDOW + 1; i++) {
	noise_stats->smooth_scaling[i] = 1.0 / i;
    }
#else
    noise_stats->lambda_power = FLOAT2FIX(log(LAMBDA_POWER));
    noise_stats->comp_lambda_power = FLOAT2FIX(log(1 - LAMBDA_POWER));
    noise_stats->lambda_a = FLOAT2FIX(log(LAMBDA_A));
    noise_stats->comp_lambda_a = FLOAT2FIX(log(1 - LAMBDA_A));
    noise_stats->lambda_b = FLOAT2FIX(log(LAMBDA_B));
    noise_stats->comp_lambda_b = FLOAT2FIX(log(1 - LAMBDA_B));
    noise_stats->lambda_t = FLOAT2FIX(log(LAMBDA_T));
    noise_stats->mu_t = FLOAT2FIX(log(1 - LAMBDA_T));
    noise_stats->max_gain = FLOAT2FIX(log(MAX_GAIN));
    noise_stats->inv_max_gain = FLOAT2FIX(log(1.0 / MAX_GAIN));

    for (i = 1; i < 2 * SMOOTH_WINDOW + 3; i++) {
	noise_stats->smooth_scaling[i] = FLOAT2FIX(log(i));
    }
#endif

    return noise_stats;
}

void
fe_reset_noisestats(noise_stats_t * noise_stats)
{
    noise_stats->undefined = TRUE;
}

void
fe_free_noisestats(noise_stats_t * noise_stats)
{
    ckd_free(noise_stats->power);
    ckd_free(noise_stats->noise);
    ckd_free(noise_stats->floor);
    ckd_free(noise_stats->peak);
    ckd_free(noise_stats);
}

/**
 * For fixed point we are doing the computation in a fixlog domain,
 * so we have to add many processing cases.
 */
void
fe_remove_noise(noise_stats_t * noise_stats, powspec_t * mfspec)
{
    powspec_t *signal;
    powspec_t *gain;
    int32 i, num_filts;

    num_filts = noise_stats->num_filters;

    signal = (powspec_t *) ckd_calloc(num_filts, sizeof(powspec_t));
    gain = (powspec_t *) ckd_calloc(num_filts, sizeof(powspec_t));

    if (noise_stats->undefined) {
        for (i = 0; i < num_filts; i++) {
            noise_stats->power[i] = mfspec[i];
            noise_stats->noise[i] = mfspec[i];
#ifdef FIXED_POINT
            noise_stats->floor[i] = mfspec[i] - noise_stats->max_gain;
            noise_stats->peak[i] = MIN_FIXLOG;
#else
            noise_stats->floor[i] = mfspec[i] / noise_stats->max_gain;
            noise_stats->peak[i] = 0.0;
#endif
        }
        noise_stats->undefined = FALSE;
    }

    /* Calculate smoothed power */
    for (i = 0; i < num_filts; i++) {
#ifdef FIXED_POINT
        noise_stats->power[i] = fe_log_add(noise_stats->lambda_power + noise_stats->power[i],
    					   noise_stats->comp_lambda_power + mfspec[i]);
#else
        noise_stats->power[i] =
            noise_stats->lambda_power * noise_stats->power[i] + noise_stats->comp_lambda_power * mfspec[i];
#endif            
    }

    /* Noise estimation */
    fe_low_envelope(noise_stats, noise_stats->power, noise_stats->noise, num_filts);

    for (i = 0; i < num_filts; i++) {
#ifndef FIXED_POINT
	signal[i] = noise_stats->power[i] - noise_stats->noise[i];
        if (signal[i] < 0)
            signal[i] = 0;
#else
        signal[i] = fe_log_sub(noise_stats->power[i], noise_stats->noise[i]);
#endif
    }

    fe_low_envelope(noise_stats, signal, noise_stats->floor, num_filts);

    fe_temp_masking(noise_stats, signal, noise_stats->peak, num_filts);

    for (i = 0; i < num_filts; i++) {
        if (signal[i] < noise_stats->floor[i])
            signal[i] = noise_stats->floor[i];
    }

#ifndef FIXED_POINT
    for (i = 0; i < num_filts; i++) {
	if (signal[i] < noise_stats->max_gain * noise_stats->power[i])
            gain[i] = signal[i] / noise_stats->power[i];
        else
            gain[i] = noise_stats->max_gain;
        if (gain[i] < noise_stats->inv_max_gain)
            gain[i] = noise_stats->inv_max_gain;
    }
#else
    for (i = 0; i < num_filts; i++) {
        gain[i] = signal[i] - noise_stats->power[i];
        if (gain[i] > noise_stats->max_gain)
            gain[i] = noise_stats->max_gain;
        if (gain[i] < noise_stats->inv_max_gain)
            gain[i] = noise_stats->inv_max_gain;
    }
#endif

    /* Weight smoothing and time frequency normalization */
    fe_weight_smooth(noise_stats, mfspec, gain, num_filts);

    ckd_free(signal);
    ckd_free(gain);
}
