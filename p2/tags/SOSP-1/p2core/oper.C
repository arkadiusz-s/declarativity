/*
 * Copyright (c) 2004 Intel Corporation. All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE file.
 * If you do not find these files, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * 
 * DESCRIPTION: P2's concrete type system.  The base type for values.
 *
 */

#include "oper.h"
#include "val_null.h"
#include "val_str.h"
#include "val_int32.h"
#include "val_uint32.h"
#include "val_int64.h"
#include "val_uint64.h"
#include "val_double.h"
#include "val_opaque.h"
#include "val_tuple.h"
#include "val_time.h"
#include "val_id.h"
#include "val_ip_addr.h"

namespace opr {

  /**
   * The initialization of the operator table for operator functions.
   * The entries are filled in based on the base type of the corresponding
   * operands. The TypeName of the operands are used to index this table.
   * Each entry for each possible combination of operand types holds the
   * location of the BASE type operator function. The base type operator
   * function is a public static member variable defined as oper_ in each P2
   * concrete type.  
   *
   * CHEAT SHEET: type of each column entry.
   * NULLV,  STR,    INT32,  
   * UINT32, INT64,  UINT64, 
   * DOUBLE, OPAQUE, TUPLE,  
   * TIME,   ID,     IP_ADDR
   */
  const Oper** Oper::oper_table_[Value::TYPES][Value::TYPES] = {
  /* NULLV */
  {&Val_Null::oper_,   &Val_Null::oper_,   &Val_Int32::oper_, 
   &Val_UInt32::oper_, &Val_Int64::oper_,  &Val_UInt64::oper_, 
   &Val_Double::oper_, &Val_Null::oper_,   &Val_Null::oper_,   
   &Val_Time::oper_,   &Val_Null::oper_, &Val_IP_ADDR::oper_},
  /* STR */
  {&Val_Null::oper_, &Val_Str::oper_,    &Val_Str::oper_,    
   &Val_Str::oper_,  &Val_Str::oper_,    &Val_Str::oper_,  
   &Val_Str::oper_,  &Val_Opaque::oper_, &Val_Tuple::oper_, 
   &Val_Str::oper_,  &Val_Str::oper_,  &Val_IP_ADDR::oper_},
  /* INT32 */
  {&Val_Null::oper_,   &Val_Str::oper_,    &Val_Int32::oper_,  
   &Val_UInt32::oper_, &Val_Int64::oper_,  &Val_UInt64::oper_, 
   &Val_Double::oper_, &Val_Opaque::oper_, &Val_Tuple::oper_,  
   &Val_Time::oper_,   &Val_ID::oper_,  &Val_IP_ADDR::oper_},
  /* UINT32 */
  {&Val_Null::oper_,   &Val_Str::oper_,    &Val_UInt32::oper_, 
   &Val_UInt32::oper_, &Val_Int64::oper_,  &Val_UInt64::oper_, 
   &Val_Double::oper_, &Val_Opaque::oper_, &Val_Tuple::oper_,  
   &Val_Time::oper_,   &Val_ID::oper_, &Val_IP_ADDR::oper_},
  /* INT64 */
  {&Val_Null::oper_,   &Val_Str::oper_,    &Val_Int64::oper_,  
   &Val_Int64::oper_,  &Val_Int64::oper_,  &Val_UInt64::oper_, 
   &Val_Double::oper_, &Val_Opaque::oper_, &Val_Tuple::oper_, 
   &Val_Time::oper_,   &Val_ID::oper_, &Val_IP_ADDR::oper_},
  /* UINT64 */
  {&Val_Null::oper_,   &Val_Str::oper_,    &Val_UInt64::oper_,  
   &Val_UInt64::oper_, &Val_UInt64::oper_, &Val_UInt64::oper_, 
   &Val_Double::oper_, &Val_Opaque::oper_, &Val_Tuple::oper_,  
   &Val_Time::oper_,   &Val_ID::oper_, &Val_IP_ADDR::oper_},
  /* DOUBLE */
  {&Val_Null::oper_,   &Val_Str::oper_,    &Val_Double::oper_, 
   &Val_Double::oper_, &Val_Double::oper_, &Val_Double::oper_, 
   &Val_Double::oper_, &Val_Opaque::oper_, &Val_Tuple::oper_,  
   &Val_Time::oper_,   &Val_ID::oper_, &Val_IP_ADDR::oper_},
  /* OPAQUE */
  {&Val_Opaque::oper_, &Val_Opaque::oper_, &Val_Opaque::oper_, 
   &Val_Opaque::oper_, &Val_Opaque::oper_, &Val_Opaque::oper_, 
   &Val_Opaque::oper_, &Val_Opaque::oper_, &Val_Opaque::oper_, 
   &Val_Opaque::oper_, &Val_Opaque::oper_, &Val_IP_ADDR::oper_},
  /* TUPLE */
  {&Val_Tuple::oper_, &Val_Tuple::oper_, &Val_Tuple::oper_, 
   &Val_Tuple::oper_, &Val_Tuple::oper_, &Val_Tuple::oper_, 
   &Val_Tuple::oper_, &Val_Tuple::oper_, &Val_Tuple::oper_, 
   &Val_Tuple::oper_, &Val_Tuple::oper_, &Val_IP_ADDR::oper_},
  /* TIME */
  {&Val_Null::oper_, &Val_Str::oper_,    &Val_Time::oper_,   
   &Val_Time::oper_, &Val_Time::oper_,   &Val_Time::oper_, 
   &Val_Time::oper_, &Val_Opaque::oper_, &Val_Tuple::oper_, 
   &Val_Time::oper_, &Val_Str::oper_, &Val_IP_ADDR::oper_},
  /* ID */
  {&Val_Null::oper_, &Val_Str::oper_,    &Val_ID::oper_,     
   &Val_ID::oper_,   &Val_ID::oper_,     &Val_ID::oper_,   
   &Val_ID::oper_,   &Val_Opaque::oper_, &Val_Tuple::oper_, 
   &Val_Str::oper_,  &Val_ID::oper_, &Val_IP_ADDR::oper_},
  /* IP_ADDR */
  {&Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_,
   &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_,
   &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_,
   &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_, &Val_IP_ADDR::oper_}
  };
  
  ValueRef operator<<(const ValueRef& v1, const ValueRef& v2) {
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_lshift(v1, v2);
  };
  ValueRef operator>>(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_rshift(v1, v2);
  };
  ValueRef operator+(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_plus(v1, v2);
  };
  ValueRef operator-(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_minus(v1, v2);
  };
  ValueRef operator--(const ValueRef& v1) { 
   return (*Oper::oper_table_[v1->typeCode()][v1->typeCode()])->_dec(v1);
  };
  ValueRef operator++(const ValueRef& v1) { 
   return (*Oper::oper_table_[v1->typeCode()][v1->typeCode()])->_inc(v1);
  };
  ValueRef operator*(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_times(v1, v2);
  };
  ValueRef operator/(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_divide(v1, v2);
  };
  ValueRef operator%(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_mod(v1, v2);
  };
  ValueRef operator~ (const ValueRef& v) {
   return (*Oper::oper_table_[v->typeCode()][v->typeCode()])->_bnot(v);
  };
  ValueRef operator& (const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_band(v1, v2);
  };
  ValueRef operator| (const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_bor(v1, v2);
  };
  ValueRef operator^ (const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_bxor(v1, v2);
  };
  
  bool operator==(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_eq(v1, v2);
  };
  bool operator!=(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_neq(v1, v2);
  };
  bool operator< (const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_lt(v1, v2);
  };
  bool operator<=(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_lte(v1, v2);
  };
  bool operator> (const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_gt(v1, v2);
  };
  bool operator>=(const ValueRef& v1, const ValueRef& v2) { 
   return (*Oper::oper_table_[v1->typeCode()][v2->typeCode()])->_gte(v1, v2);
  };
  
  bool inOO(const ValueRef& v1, const ValueRef& v2, const ValueRef& v3) {
   return (*Oper::oper_table_[v2->typeCode()][v3->typeCode()])->_inOO(v1, v2, v3);
  };
  bool inOC(const ValueRef& v1, const ValueRef& v2, const ValueRef& v3) {
   return (*Oper::oper_table_[v2->typeCode()][v3->typeCode()])->_inOC(v1, v2, v3);
  };
  bool inCO(const ValueRef& v1, const ValueRef& v2, const ValueRef& v3) {
   return (*Oper::oper_table_[v2->typeCode()][v3->typeCode()])->_inCO(v1, v2, v3);
  };
  bool inCC(const ValueRef& v1, const ValueRef& v2, const ValueRef& v3) {
   return (*Oper::oper_table_[v2->typeCode()][v3->typeCode()])->_inCC(v1, v2, v3);
  };
}; // END NAMESPACE OPR

/*
 * oper.C
 */
