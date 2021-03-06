// -*- c-basic-offset: 2; related-file-name: "joiner.C" -*-
/*
 * @(#)$Id$
 * 
 * This file is distributed under the terms in the attached INTEL-LICENSE file.
 * If you do not find these files, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * 
 * DESCRIPTION: Element that performs the merger of a tuple from one
 * relation of a join with all other tuples from the other relation that
 * can be joined with it. Given one tuple on one input, it waits for all
 * tuples on the other input until the end of stream tag is received,
 * and produces their mergers, placing all fields of both tuples into
 * the result.  The first input acts as a gate. If empty, the second
 * input does not accept
 */

#ifndef __JOINER_H__
#define __JOINER_H__

#include "element.h"

class Joiner : public Element { 
public:

  Joiner(str);

  const char *class_name() const		{ return "Joiner";}
  const char *processing() const		{ return "lh/l"; }
  const char *flow_code() const			{ return "--/-"; }

  /** Receive a new fixed tuple */
  int push(int port, TupleRef, cbv cb);

  /** Return a new joined tuple. */
  TuplePtr pull(int port, cbv cb);

private:

  /** My fixed tuple */
  TuplePtr _fixed;

  /** My pusher's callback */
  cbv _pushCallback;
  
  /** My puller's callback */
  cbv _pullCallback;

  /** My tuple merging method.  If I have a pair, return a tuple
      containing both. If I had an empty search, return nothing.  */
  TuplePtr mergeTuples(TupleRef);
};

#endif /* __JOINER_H_ */
