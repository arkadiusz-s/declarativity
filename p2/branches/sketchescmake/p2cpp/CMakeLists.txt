ADD_LIBRARY(libp2cpp p2.C)
INCLUDE_DIRECTORIES(${CMAKE_CURRENT_SOURCE_DIR} ${P2_BINARY_DIR}/overlog 			
					../elements 
					../net 
					../debugger
					../pythonElements
					../eventLoop
					../overlog )
