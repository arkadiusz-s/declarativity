// -*- c-basic-offset: 2; related-file-name: "slot.C" -*-
/*
 * @(#)$Id$
 * 
 * This file is distributed under the terms in the attached INTEL-LICENSE file.
 * If you do not find these files, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * 
 * DESCRIPTION: Single-tuple queue element for P2 (basically, an
 * explicit scheduling point in the dataflow graph)
 */

#ifndef __SLOT_H__
#define __SLOT_H__

#include "element.h"

class Slot : public Element { 
public:
  
  Slot(str name);

  int push(int port, TupleRef t, cbv cb);
  TuplePtr pull(int port, cbv);
  const char *class_name() const		{ return "Slot";}
  const char *processing() const		{ return PUSH_TO_PULL; }
  const char *flow_code() const			{ return "-/-"; }

private:
  TuplePtr _t;
  cbv	_push_cb;
  cbv	_pull_cb;
};


#endif /* __SLOT_H_ */
