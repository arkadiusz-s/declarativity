########################################################################
#
#  @(#)$Id:$
#
# This file is distributed under the terms in the attached LICENSE file.
# If you do not find this file, copies can be found by writing to:
# Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300,
# Berkeley, CA, 94704.  Attention:  Intel License Inquiry.
# Or
# UC Berkeley EECS Computer Science Division, 387 Soda Hall #1776, 
# Berkeley, CA,  94707. Attention: P2 Group.
#
# DESCRIPTION: Makefile for the P2 core library
#
SUBDIRS(pel)

ADD_LIBRARY(p2core
	    val_factor.C
            CSVlex.C
            ID.C 
            aggFactory.C 
            commitManager.C
            commonTable.C 
            compileUtil.C
            dot.C 
            element.C 
            elementSpec.C 
            fdbuf.C
            list.C 
            loggerI.C 
            loop.C
            oper.C
            p2Time.C 
            plumber.C 
	    dataflow.C
	    dataflowEdit.C
            refTable.C 
            reporting.C
            scheduler.C 
            table2.C 
            tableManager.C
            tuple.C 
            set.C 
            val_set.C 
            val_double.C 
	    val_id.C 
            val_int64.C 
            val_list.C 
            val_matrix.C
            val_null.C 
            val_opaque.C 
            val_str.C 
            val_time.C 
            val_tuple.C 
            val_vector.C 
            value.C 
            xdrbuf.C 
)

find_package(LibXml++)

add_definitions(${LIBXMLPP_DEFINITIONS})
include_directories(${LIBXMLPP_INCLUDE_DIR})
link_libraries(${LIBXMLPP_LIBRARIES})
link_directories(${LIBXMLPP_LINK_DIR})

TARGET_LINK_LIBRARIES(p2core blas lapack boost_regex${TOOLSET}-mt-1_35 boost_serialization${TOOLSET}-mt-1_35 boost_date_time${TOOLSET}-mt-1_35 crypto ssl boost_thread${TOOLSET}-mt-1_35) 

ADD_DEFINITIONS(-DBOOST_UBLAS_ENABLE_PROXY_SHORTCUTS)

INCLUDE_DIRECTORIES(${CMAKE_CURRENT_BINARY_DIR})

INSTALL(TARGETS p2core LIBRARY DESTINATION lib
		       ARCHIVE DESTINATION lib)
