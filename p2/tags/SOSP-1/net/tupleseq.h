// -*- c-basic-offset: 2; related-file-name: "element.C" -*-
/*
 * This file is distributed under the terms in the attached INTEL-LICENSE file.
 * If you do not find these files, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * 
 */

#ifndef __TupleSeq_H__
#define __TupleSeq_H__

#include "element.h"
#include "val_uint64.h"

#define MAKE_SEQ(s, o) (((s) << 4) | ((o) & 0xF))
#define SEQ_NUM(n)     ((n) >> 4)
#define OFFSET(n)      ((int)((n) & 0xF))

class Sequence : public Element {
public:
  Sequence(str name="Sequence", u_int64_t start_seq=1);
  const char *class_name() const { return "Sequence";};
  const char *processing() const { return "a/a"; };
  const char *flow_code() const	 { return "x/x"; };

  TuplePtr simple_action(TupleRef p);	// Adds the next sequence num to tuple stream. 

private:
  u_int64_t seq_;
};

#endif /* __TupleSeq_H_ */
