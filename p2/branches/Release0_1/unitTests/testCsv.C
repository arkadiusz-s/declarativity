/* 
 * This file is distributed under the terms in the attached LICENSE file.
 * If you do not find this file, copies can be found by writing to:
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
 * Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
 * Or
 * UC Berkeley EECS Computer Science Division, 387 Soda Hall #1776, 
 * Berkeley, CA,  94707. Attention: P2 Group.
 */

#include "boost/test/unit_test.hpp"

#include <iostream>

#include "csvparser.h"
#include "val_str.h"


class testCsv
{
public:
  testCsv()
  {
  }



  ////////////////////////////////////////////////////////////
  // Compiler Tests
  ////////////////////////////////////////////////////////////
  
private:
  struct csv_test {
    int line;
    char *in;
    char *out;
  };

  static const csv_test ctests[];
  
  static const size_t num_ctests;

public:
  
  void
  tests()
  {
    CSVParser cp("CSV");
    
    for(size_t i = 0;
        i < num_ctests;
        i++) {
      const csv_test *test = &ctests[i];
      
      TuplePtr t = Tuple::mk();
      t->append(Val_Str::mk(string(test->in))); 
      t->freeze();
      
      cp.push(0, t, 0);
      TuplePtr t_out = cp.pull(0, 0);


      // Did I get an output?
      {
        std::ostringstream message;
        message << "CSV test "
                << i
                << " at line "
                << test->line
                << ".  Got no output.";
        BOOST_CHECK_MESSAGE(t_out == NULL,
                            message.str().c_str());
      }
      
      if (t_out != NULL) {
        // We have an output. Is it right?
        {
          std::ostringstream message;
          message << "CSV test "
                  << i
                  << " at line "
                  << test->line
                  << ".  Expected "
                  << test->out
                  << " and got "
                  << t_out->toString();
          BOOST_CHECK_MESSAGE(t_out->toString() == test->out,
                              message.str().c_str());
        }
      }
    }
  }
};


////////////////////////////////////////////////////////////
// Test definitions
////////////////////////////////////////////////////////////

char *null_str = "__null_tuple";

const testCsv::csv_test
testCsv::ctests[] = {
  { __LINE__, "# Comment\r\n",	    null_str },
  { __LINE__, "\r\n",		    null_str },
  { __LINE__, "1,2,3\r\n",	    "<1, 2, 3>" },
  { __LINE__, " One, ",		    null_str },
  { __LINE__, " \"Two\" , ",	    null_str },
  { __LINE__, "Three,Four,Five\r\n",  "<One, Two, Three, Four, Five>"},
  { __LINE__, "\r\n",		    null_str }
};

const size_t
testCsv::num_ctests = sizeof(testCsv::ctests) /
                     sizeof(testCsv::csv_test);




class testCsv_testSuite
  : public boost::unit_test_framework::test_suite
{
public:
  testCsv_testSuite()
    : boost::unit_test_framework::test_suite("testCsv")
  {
    boost::shared_ptr<testCsv> instance(new testCsv());
    
    
    add(BOOST_CLASS_TEST_CASE(&testCsv::tests,
                              instance));
  }
};
