/*
 * @(#)$Id$
 * 
 * This file is distributed under the terms in the attached INTEL-LICENSE file.
 * If you do not find these files, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * Or
 * UC Berkeley EECS Computer Science Division, 387 Soda Hall #1776, 
 * Berkeley, CA,  94707. Attention: P2 Group.
 * 
 *
 */

#ifndef __AGG_H__
#define __AGG_H__

#include "element.h"
#include <queue>

class Agg : public Element { 
public:

  Agg(string name, std::vector<unsigned int> groupByFields, 
      int aggField, unsigned uniqueField, string aggStr);

  ~Agg();
  
  int push(int port, TuplePtr p, b_cbv cb);
  TuplePtr pull(int port, b_cbv cb);  

  const char *class_name() const		{ return "Agg";}
  const char *processing() const		{ return "h/l"; }
  const char *flow_code() const			{ return "x/x"; }

private:
  string getGroupByFields(TuplePtr p);
  bool checkBestTuple(TuplePtr p);
  void updateBestTuple(TuplePtr p);

  b_cbv _pullCB, _pushCB;
  std::map<string, TuplePtr> _buffer; // best values to send 
  std::map<string, TuplePtr> _bestSoFar; // best tuple so far
  std::vector<unsigned int> _groupByFields;
  std::map<string, TuplePtr> _allValues;
  int _aggField;
  unsigned _uniqueField;
  string _aggStr;
};


#endif 
