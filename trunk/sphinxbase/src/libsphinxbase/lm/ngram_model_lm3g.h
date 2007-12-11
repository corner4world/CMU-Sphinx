/* -*- c-basic-offset: 4; indent-tabs-mode: nil -*- */
/* ====================================================================
 * Copyright (c) 1999-2007 Carnegie Mellon University.  All rights
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
/*
 * \file ngram_model_lm3g.h Core Sphinx 3-gram code used in
 * DMP/DMP32/ARPA (for now) model code.
 *
 * Author: A cast of thousands, probably.
 */

#ifndef __NGRAM_MODEL_LM3G_H__
#define __NGRAM_MODEL_LM3G_H__

#include "ngram_model_internal.h"

/**
 * Type used to store language model probabilities
 */
typedef union {
    float32 f;
    int32 l;
} lmprob_t;

/**
 * Unigram structure.
 */
typedef struct unigram_s {
    lmprob_t prob1;     /**< Unigram probability. */
    lmprob_t bo_wt1;    /**< Unigram backoff weight. */
    int32 bigrams;	/**< Index of 1st entry in lm_t.bigrams[] */
} unigram_t;

/*
 * To conserve space, bigram info is kept in many tables.  Since the number
 * of distinct values << #bigrams, these table indices can be 16-bit values.
 * prob2 and bo_wt2 are such indices, but keeping trigram index is less easy.
 * It is supposed to be the index of the first trigram entry for each bigram.
 * But such an index cannot be represented in 16-bits, hence the following
 * segmentation scheme: Partition bigrams into segments of BG_SEG_SZ
 * consecutive entries, such that #trigrams in each segment <= 2**16 (the
 * corresponding trigram segment).  The bigram_t.trigrams value is then a
 * 16-bit relative index within the trigram segment.  A separate table--
 * lm_t.tseg_base--has the index of the 1st trigram for each bigram segment.
 */
#define BG_SEG_SZ	512	/* chosen so that #trigram/segment <= 2**16 */
#define LOG_BG_SEG_SZ	9
#define TSEG_BASE(m,b)		((m)->tseg_base[(b)>>LOG_BG_SEG_SZ])

#endif /* __NGRAM_MODEL_LM3G_H__ */
