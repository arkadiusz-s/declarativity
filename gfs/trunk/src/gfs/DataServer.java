package gfs;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class DataServer implements Runnable {
    private static class DataWorker implements Runnable {
        private Socket socket;
        private String clientAddr;
        private DataInputStream in;
        
        DataWorker(Socket socket) throws IOException {
            this.socket = socket;
            this.clientAddr = socket.toString();
            this.in = new DataInputStream(socket.getInputStream());
        }

        public void run() {
            while (true) {
                try {
                    try {
                        readCommand();
                    } catch (EOFException e) {
                        if (!this.socket.isClosed())
                            this.socket.close();

                        System.out.println("got EOF from " + this.clientAddr);
                        break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        
        private void readCommand() throws IOException {
            byte opCode = this.in.readByte();
            
            switch (opCode) {
            case DataProtocol.READ_OPERATION:
                doReadOperation();
                break;
            case DataProtocol.WRITE_OPERATION:
                doWriteOperation();
                break;
            default:
                throw new IOException("Unrecognized opcode: " + opCode);
            }
        }

        private void doWriteOperation() {
            // TODO Auto-generated method stub
        }

        private void doReadOperation() {
            // TODO Auto-generated method stub            
        }
    }

    private ThreadGroup workers;
    private ServerSocket serverSocket;

    DataServer(int port) {
        this.workers = new ThreadGroup("DataWorkers");

        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        while (true) {
            try {
                Socket socket = this.serverSocket.accept();

                DataWorker dw = new DataWorker(socket);
                Thread t = new Thread(this.workers, dw);
                t.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
