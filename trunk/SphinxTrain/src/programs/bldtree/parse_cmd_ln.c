/* ====================================================================
 * Copyright (c) 1994-2000 Carnegie Mellon University.  All rights 
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
 * 3. The names "Sphinx" and "Carnegie Mellon" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. To obtain permission, contact 
 *    sphinx@cs.cmu.edu.
 *
 * 4. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by Carnegie
 *    Mellon University (http://www.speech.cs.cmu.edu/)."
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
/*********************************************************************
 *
 * File: cmd_ln.c
 * 
 * Description: 
 * 
 * Author: 
 * 
 *********************************************************************/

#include "parse_cmd_ln.h"

#include <s3/cmd_ln.h>
#include <s3/err.h>

#include <stdlib.h>

int
parse_cmd_ln(int argc, char *argv[])
{
    static arg_def_t defn[] = {
	{ "-treefn",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "Name of output tree file to produce" },

	{ "-moddeffn",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "Model definition file of the discrete models" },

/* ADDITION FOR CONTINUOUS_TREES */
        { "-ts2cbfn",
          CMD_LN_STRING,
          CMD_LN_NO_VALIDATION,
          ".semi.",
          "The type of models to build trees on" },

	{ "-meanfn",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "means file for tree building using continuous HMMs" },

	{ "-varfn",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "variances file for tree building using continuous HMMs" },

        { "-varfloor",
          CMD_LN_FLOAT32,
          CMD_LN_NO_VALIDATION,
          "0.00001",
          "The minimum variance"},

        { "-cntthresh",
          CMD_LN_FLOAT32,
          CMD_LN_NO_VALIDATION,
          "0.00001",
          "Ignore all states with counts less than this"},

/* END ADDITION FOR CONTINUOUS_TREES */

	{ "-mixwfn",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "PDF's for tree building using semicontinuous HMMs" },

	{ "-psetfn",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "phone set definitions for phonetic questions" },

	{ "-phone",
	  CMD_LN_STRING,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "Build trees over n-phones having this base phone"},

	{ "-state",
	  CMD_LN_INT32,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "Build tree for this state position"},

	{ "-mwfloor",
	  CMD_LN_FLOAT32,
	  CMD_LN_NO_VALIDATION,
	  "1e-4",
	  "Mixing weight floor for tree building"},

	{ "-stwt",
	  CMD_LN_STRING_LIST,
	  CMD_LN_NO_VALIDATION,
	  CMD_LN_NO_DEFAULT,
	  "Weights on neighboring states" },

	{ "-ssplitthr",
	  CMD_LN_FLOAT32,
	  CMD_LN_NO_VALIDATION,
	  "8e-4",
	  "Simple node splitting threshold" },

	{ "-ssplitmin",
	  CMD_LN_INT32,
	  CMD_LN_NO_VALIDATION,
	  "1",
	  "Minimum # of simple tree splits to do" },

	{ "-ssplitmax",
	  CMD_LN_INT32,
	  CMD_LN_NO_VALIDATION,
	  "5",
	  "Minimum # of simple tree splits to do" },

	{ "-csplitthr",
	  CMD_LN_FLOAT32,
	  CMD_LN_NO_VALIDATION,
	  "8e-4",
	  "Compound node splitting threshold" },

	{ "-csplitmin",
	  CMD_LN_INT32,
	  CMD_LN_NO_VALIDATION,
	  "1",
	  "Minimum # of compound tree splits to do" },

	{ "-csplitmax",
	  CMD_LN_INT32,
	  CMD_LN_NO_VALIDATION,
	  "100",
	  "Minimum # of compound tree splits to do" },

	{ NULL, CMD_LN_UNDEF, CMD_LN_NO_VALIDATION, CMD_LN_NO_DEFAULT, NULL }
    };

    cmd_ln_define(defn);

    if (argc == 1) {
	cmd_ln_print_definitions();
	exit(1);
    }

    cmd_ln_parse(argc, argv);

    if (cmd_ln_validate() == FALSE) {
	E_FATAL("Unable to validate command line arguments\n");
    }

    cmd_ln_print_configuration();

    return 0;
}

/*
 * Log record.  Maintained by RCS.
 *
 * $Log$
 * Revision 1.1  2004/06/17  19:39:46  arthchan2003
 * add back all command line information into the code
 * 
 * Revision 1.3  2001/04/05 20:02:31  awb
 * *** empty log message ***
 *
 * Revision 1.2  2000/09/29 22:35:13  awb
 * *** empty log message ***
 *
 * Revision 1.1  2000/09/24 21:38:31  awb
 * *** empty log message ***
 *
 * Revision 1.1  97/07/16  11:36:22  eht
 * Initial revision
 * 
 *
 */
