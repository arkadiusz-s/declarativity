// -*- c-basic-offset: 2; related-file-name: "element.h" -*-
/*
 * @(#)$Id$
 *
 * Modified from the Click Element base class by Eddie Kohler
 * statistics: Robert Morris
 * P2 version: Timothy Roscoe
 * 
 * Copyright (c) 1999-2000 Massachusetts Institute of Technology
 * Copyright (c) 2004 Regents of the University of California
 * Copyright (c) 2004 Intel Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software")
 * to deal in the Software without restriction, subject to the conditions
 * listed in the Click LICENSE file. These conditions include: you must
 * preserve this copyright notice, and you cannot mention the copyright
 * holders in advertising related to the Software without their permission.
 * The Software is provided WITHOUT ANY WARRANTY, EXPRESS OR IMPLIED. This
 * notice is a summary of the Click LICENSE file; the license in that file is
 * legally binding.
 * 
 * This file is distributed under the terms in the attached LICENSE file.
 * If you do not find this file, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * Or
 * UC Berkeley EECS Computer Science Division, 387 Soda Hall #1776, 
 * Berkeley, CA,  94707. Attention: P2 Group.
 * 
 * DESCRIPTION: Base class for P2 elements
 *
 * Note to P2 hackers: much has been removed from the original Click
 * class.  Much of that may well find its way back in, but for now
 * let's put stuff back in when we understand why we need it.  That
 * way we'll all develop a better understanding of why Click (and P2)
 * are the way they are. 
 *
 * An element may be in one of two states: CONFIGURATION and RUNNING.
 * During configuration, the element maintains data about its ports
 * (including personalities, flow codes, etc.) that faciliate with
 * static analysis of the big picture of the forwarding engine.  Since
 * all those data aare not useful once the element is up and running, we
 * only keep them during configuration and discard them thereafter.
 */

#include <errno.h>
#include "element.h"
#include "plumber.h"
#include "loggerI.h"
#include "p2Time.h"

#include "val_double.h"
#include "val_uint64.h"
#include "val_str.h"
#include "val_int32.h"
#include "val_time.h"
#include "dlogger.h"


// Some basic element types
const char * const Element::PUSH_TO_PULL = "h/l";
const char * const Element::PULL_TO_PUSH = "l/h";

// A basic flow code
const char * const Element::COMPLETE_FLOW = "x/x";

// For use in default-logger sequencing
uint64_t Element::seq=0;

int Element::nelements_allocated = 0;
int Element::elementCounter = 0;

// Debugging Logger
DLogger * Element::_debuggingLogger = NULL;

#if P2_STATS >= 2
# define ELEMENT_CTOR_STATS _calls(0), _self_cycles(0), _child_cycles(0),
#else
# define ELEMENT_CTOR_STATS
#endif

static inline string mk_id_str(long id)
{
  ostringstream s; 
  s << id;
  return s.str();
}

Element::Element(string instanceName) :
  ELEMENT_CTOR_STATS
  _ninputs(0),
  _noutputs(0),
  _ID(elementCounter++),
  _name(instanceName),
  _IDstr(mk_id_str(_ID))
{
  nelements_allocated++;
  initDebugParams();
}

Element::Element(string instanceName, int ninputs, int noutputs) :
  ELEMENT_CTOR_STATS
  _ninputs(0),
  _noutputs(0),
  _ID(elementCounter++),
  _name(instanceName),
  _IDstr(mk_id_str(_ID))
{
  set_nports(ninputs, noutputs);
  nelements_allocated++;
  initDebugParams();
}

Element::~Element()
{
  nelements_allocated--;
}

// initialize debugging parameters
void
Element::initDebugParams()
{
  
  _doLogging = false;
  _doCounting = false;
  _ruleId = "$";
  _action = "-";
  _status = -1;
  _nodeId = "-";
  _ruleNum = -1;
  _preConditionOrder = -1;
}

// INPUTS AND OUTPUTS

void
Element::set_nports(int new_ninputs, int new_noutputs)
{
  // exit on bad counts, or if already initialized
  // XXX initialized flag for element
  if (new_ninputs < 0 || new_noutputs < 0)
    return;
  
  // Enlarge port arrays if necessary
  _inputs.resize(new_ninputs);
  _ninputs = new_ninputs;
  for (int i = 0;
       i < new_ninputs;
       i++) {
    _inputs[i].reset(new Port());
  }

  _outputs.resize(new_noutputs);
  _noutputs = new_noutputs;
  for (int i = 0;
       i < new_noutputs;
       i++) {
    _outputs[i].reset(new Port());
  }
}

void
Element::set_ninputs(int count)
{
  set_nports(count, _noutputs);
}

void
Element::set_noutputs(int count)
{
  set_nports(_ninputs, count);
}

bool
Element::ports_frozen() const
{
  assert(0);                    // Deal with freezing without port0
  return false;
}

int Element::connect_input(int i, Element *f, int port)
{
  if (i >= 0 && i < ninputs()) {
    _inputs[i].reset(new Port(this, f, port));
    return 0;
  } else
    return -1;
}

int Element::connect_output(int o, Element *f, int port)
{
  if (o >= 0 && o < noutputs()) {
    _outputs[o].reset(new Port(this, f, port));
    return 0;
  } else
    return -1;
}

// PUSH OR PULL PROCESSING

const char * Element::processing() const
{
  return "a/a";
}

const char *Element::flow_code() const
{
  return COMPLETE_FLOW;
}

const char *Element::flags() const
{
  return "";
}

// RUNNING

int Element::push(int port, TuplePtr p, b_cbv cb)
{
  assert(p != 0);

  // Apply the action
  TuplePtr result = simple_action(p);

  // Did we get a result?
  if (result == 0 || result->size() == 0) {
    // No result
    log(LoggerI::WARN,
        -1,
        "push: Input tuple yielded no output tuple");
    return 1;
  } else {
    return output(0)->push(result, cb);
  }
}

TuplePtr Element::pull(int port, b_cbv cb)
{
  while (1) {
    TuplePtr p = input(0)->pull(cb);
    if (p) {
      TuplePtr result = simple_action(p);
      if (result != 0) {
        return result;
      } else {
        // This input yielded no result. Try again.
        log(LoggerI::WARN,
            -1,
            "pull: Input tuple yielded no output tuple");
      }
    } else {
      // Didn't get any tuples from my input. Fail.
      return TuplePtr();
    }
  }
}

TuplePtr Element::simple_action(TuplePtr p)
{
  return p;
}

REMOVABLE_INLINE const Element::PortPtr Element::input(int i) const
{
  assert(i >= 0 && i < ninputs());
  return _inputs[i];
}

REMOVABLE_INLINE const Element::PortPtr Element::output(int o) const
{
  assert(o >= 0 && o < noutputs());
  return _outputs[o];
}

////////////////////////////////////////////////////////////
// Element::Port


REMOVABLE_INLINE int Element::Port::push(TuplePtr p, b_cbv cb) const
{
  // If I am not connected, I shouldn't be pushed.
  assert(_e);
#if P2_STATS >= 1
  _tuples++;
#endif

  int returnValue;
#if P2_STATS >= 2
  _e->input(_port)._tuples++;
  uint64_t c0 = click_get_cycles();
#endif // P2_STATS >= 2

  //returnValue = _e->push(_port, p, cb);
  returnValue = _e->input(_port)->push_incoming(_port, p, cb);

#if P2_STATS >= 2
  uint64_t c1 = click_get_cycles();
  uint64_t x = c1 - c0;
  _e->_calls += 1;
  _e->_self_cycles += x;
  _owner->_child_cycles += x;
#endif

  return returnValue;
}

REMOVABLE_INLINE TuplePtr Element::Port::pull(b_cbv cb) const
{
  // If I am not connected, I shouldn't be pulled.
  assert(_e);
#if P2_STATS >= 2
  _e->output(_port)._tuples++;
  uint64_t c0 = click_get_cycles();
#endif

  //TuplePtr p = _e->pull(_port, cb);
  TuplePtr p = _e->output(_port)->pull_outgoing(_port, cb);

#if P2_STATS >= 2
  uint64_t c1 = click_get_cycles();
  uint64_t x = c1 - c0;
  _e->_calls += 1;
  _e->_self_cycles += x;
  _owner->_child_cycles += x;
#endif

#if P2_STATS >= 1
  if (p) _tuples++;
#endif

  return p;
}

REMOVABLE_INLINE int Element::Port::push_incoming(int port, TuplePtr p, b_cbv cb) const
{
  // status ==> where in the rule strand does this element stand
  // status == 0 ==> start of the rule
  // this is based on the assumption that tuples are pushed at the beg. of rule 

  if(p && _owner->_doLogging && port == 0 && _owner->_status == 0)
    _owner->_debuggingLogger->put(p, _owner, 0);

  return _owner->push(port, p, cb);
}

REMOVABLE_INLINE TuplePtr Element::Port::pull_outgoing(int port, b_cbv cb ) const
{
  // status ==> where in the rule strand does this element stand
  // status == 1 ==> end of the rule
  // this is based on the assumption that tuples are pulled from the end of the rule

  TuplePtr t = _owner->pull(port, cb);
  if(t && _owner->_doLogging && port == 0 && _owner->_status == 1)
    _owner->_debuggingLogger->put(t, _owner, 1);

  return t;
}


/** Construct a detached free port */
REMOVABLE_INLINE Element::Port::Port() :
  _e(0),
  _port(NOT_INITIALIZED),
  _cb(0)
  PORT_CTOR_INIT(0)
{ }

/** Construct an attached port */
REMOVABLE_INLINE Element::Port::Port(Element *owner,
                                     Element *e,
                                     int p)
  : _e(e),
    _port(p),
    _cb(0)
  PORT_CTOR_INIT(owner)
{
  (void) owner;
}

int Element::initialize()
{
  return 0;
}

REMOVABLE_INLINE void Element::log(LoggerI::Level severity,
                                   int errnum,
                                   string explanation)
{
  // Even this is a shortcut, cut off the process here as well, since
  // creating the instance name is expensive
  if ((_plumber && (severity >= _plumber->loggingLevel)) || (!_plumber)) {
    ostringstream n;
    n << _name << ":" << _IDstr;
    log(n.str(), severity, errnum, explanation);
  }
}

REMOVABLE_INLINE void Element::log(string instanceName,
                                   LoggerI::Level severity,
                                   int errnum,
                                   string explanation)
{
  // Check logging level first
  if (_plumber != 0) {
    if (severity >= _plumber->loggingLevel) {
      LoggerI* l = _plumber->logger();
      if (l != 0) {
        l->log(class_name(),
               instanceName,
               severity,
               errnum,
               explanation); 
      } else {
        logDefault(instanceName, severity, errnum, explanation);
      }
    } else {
      logDefault(instanceName, severity, errnum, explanation);
    }
  } else {
    logDefault(instanceName, severity, errnum, explanation);
  }
}

REMOVABLE_INLINE void Element::logDefault(string instanceName,
                                          LoggerI::Level severity,
                                          int errnum,
                                          string explanation)
{
  struct timespec now;
  getTime(now);
  
  TuplePtr t = Tuple::mk();
  t->append(Val_Time::mk(now));
  t->append(Val_UInt64::mk(seq++));
  t->append(Val_Str::mk(class_name()));
  t->append(Val_Str::mk(instanceName));
  t->append(Val_Int32::mk(severity));
  t->append(Val_Int32::mk(errnum));
  t->append(Val_Str::mk(explanation));
  t->freeze();
  warn << "PRE-init LOGGER: " << t->toString() << "\n";
}
                            
