/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electronic Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */


package edu.cmu.sphinx.frontend;

/**
 * A signal that indicates the end of data.
 *
 * @see Data
 * @see DataProcessor
 * @see Signal
 */
public class DataEndSignal extends Signal {

    /**
     * Constructs a DataEndSignal.
     */
    public DataEndSignal() {
        this(System.currentTimeMillis());
    }

    /**
     * Constructs a DataEndSignal with the given creation time.
     *
     * @param time the creation time of the DataEndSignal
     */
    public DataEndSignal(long time) {
        super(time);
    }
}
