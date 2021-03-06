// -*- c-basic-offset: 2; related-file-name: "XTraceTail.C" -*-
/*
 * @(#)$$
 *
 * This file is distributed under the terms in the attached LICENSE file.
 * If you do not find this file, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * Or
 * UC Berkeley EECS Computer Science Division, 387 Soda Hall #1776, 
 * Berkeley, CA,  94707. Attention: P2 Group.
 * 
 * DESCRIPTION: A CSV tokenizing stage.
 *
 */

#ifndef _XTRACETAIL_H_
#define _XTRACETAIL_H_

#include <string>
#include <queue>
#include <iostream>
#include <fstream>
#include <boost/regex.hpp>
#include <sys/stat.h>
#include <fcntl.h>
#include "stage.h"
#include "val_str.h"
#include "stageRegistry.h"
#include "XTraceLex.h"

class XTraceTail : public Stage::Processor {
public:
  XTraceTail(Stage* myStage);

  ~XTraceTail() {};

  /** The input schema for XTraceTail is <tupleName, location specifier,
      delimiterString, filename>. The output is <tupleName,
      location specifier, fields...>.*/
  void
  newInput(TuplePtr inputTuple);


  std::pair< TuplePtr, Stage::Status >
  newOutput();


  // This is necessary for the class to register itself with the
  // factory.
  DECLARE_PUBLIC_STAGE_INITS(XTraceTail)




private:
	// a CVS file blocksize to go after
	static const int _blockSize = 8096;
	
 // (TaskId, OpId, ChainId, Key, Value)
  std::queue<TuplePtr> _q; // queue for emitting output
  //std::queue<TuplePtr> _tempQ; // temporary queue to store values which are not ready to be emitted 
  std::queue<TuplePtr> _edgeQ;

  string 	_TaskId, _OpId, _ChainId;
  string	_Host, _Agent, _Label, _TS, _Next_host;

  bool	_bGotIds;

  // The current string accumulator
  string	_acc;


  /** file name string */

//std::ifstream _fileStream;
    string _fileName;
    int _fd;		/* opened fd, or <= 0 if not opened		*/
    long _size;		/* size of entry last time checked		*/
    long _mtime;	/* modification time last time checked		*/

  /** The name of output tuples */
  static ValuePtr XTraceTailVal;


  /** My current location specifier */
  ValuePtr _locationSpecifier;


  // This is necessary for the class to register itself with the
  // factory.
  DECLARE_PRIVATE_STAGE_INITS
};

#endif
