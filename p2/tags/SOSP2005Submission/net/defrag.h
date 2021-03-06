// -*- c-basic-offset: 2; related-file-name: "element.C" -*-
/*
 * This file is distributed under the terms in the attached INTEL-LICENSE file.
 * If you do not find these files, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * 
 */

#ifndef __Defrag_H__
#define __Defrag_H__

#include <map>
#include <deque>
#include "tuple.h"
#include "element.h"


class Defrag : public Element { 
public:

  Defrag(str name);
  const char *class_name() const	{ return "Defrag";};
  const char *processing() const	{ return PUSH_TO_PULL; };
  const char *flow_code()  const	{ return "-/-"; };

  int push(int port, TupleRef t, cbv cb);

  TuplePtr pull(int port, cbv cb);

 private:
  cbv _push_cb;
  cbv _pull_cb;

  void defragment(TupleRef t);

  typedef std::multimap <uint64_t, TuplePtr> FragMap;
  typedef std::deque <TuplePtr> DefragQ;

  FragMap fragments_;		// Holds fragments waiting to be merged
  DefragQ tuples_;		// Defragmented tuple queue
};

#endif /* __Defrag_H_ */
