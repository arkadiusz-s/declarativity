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
using boost::unit_test_framework::test_suite; 

#include "testMarshal.C"
#include "testBasicElementPlumbing.C"
#include "testFdbufs.C"
#include "testPel.C"
#include "testValues.C"
#include "testCsv.C"
#include "testTables.C"
#include "testIDs.C"

test_suite* init_unit_test_suite(int, char**)
{
  test_suite *top = BOOST_TEST_SUITE("P2 Unit Test Suite");
  
  top->add(new testMarshal_testSuite());
  top->add(new testBasicElementPlumbing_testSuite());
  top->add(new testFdbufs_testSuite());
  top->add(new testPel_testSuite());
  top->add(new testValues_testSuite());
  top->add(new testCsv_testSuite());
  top->add(new testTables_testSuite());
  top->add(new testIDs_testSuite());

  return top;
}
