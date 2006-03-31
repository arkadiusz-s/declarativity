#include <val_int32.h>
#include <boost/python.hpp>
using namespace boost::python;

void export_val_int32()
{
  class_<Val_Int32, bases<Value>, boost::shared_ptr<Val_Int32> >
        ("Val_Int32", no_init)
    .def("mk",  &Val_Int32::mk)
    .staticmethod("mk")
  ; 
}
