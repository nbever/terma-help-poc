/*
 * Copyright (c) 2018 XMLmind Software. All rights reserved.
 *
 * Author: Hussein Shafie
 *
 * This file is part of several XMLmind projects.
 * For conditions of distribution and use, see the accompanying LEGAL.txt file.
 */
package com.xmlmind.util;

import java.io.UnsupportedEncodingException;
import java.io.PrintStream;
import java.io.OutputStream;

/**
 * Utility class allowing to redirect all the messages printed to
 * <code>System.out</code> and <code>System.err</code> to a {@link Console}.
 */
public final class ConsoleCapture {
    /**
     * The {@link Console} to which all the messages are redirected.
     */
    public final Console console;

    private static final int[] nesting = new int[1];
    private static PrintStream systemOut;
    private static PrintStream systemErr;

    // -----------------------------------------------------------------------

    private static final class OutputBuffer extends OutputStream {
        private final Console console;
        private final Console.MessageType messageType;

        private final byte[] bytes1;
        private byte[] byteBuffer;
        private int byteCount;

        public OutputBuffer(Console console, Console.MessageType messageType) {
            this.console = console;
            this.messageType = messageType;

            bytes1 = new byte[1];
            byteBuffer = new byte[6];
            byteCount = 0;
        }

        public synchronized void write(int b) {
            bytes1[1] = (byte) (b & 0xFF);
            write(bytes1, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (len > 0) {
                if (byteCount + len > byteBuffer.length) {
                    byte[] newBuffer = new byte[Math.max(2*byteBuffer.length,
                                                         byteCount + len)];
                    System.arraycopy(byteBuffer, 0, newBuffer, 0, byteCount);
                    byteBuffer = newBuffer;
                }

                System.arraycopy(b, off, byteBuffer, byteCount, len);
                byteCount += len;

                flush();
            }
        }

        @Override
        public synchronized void flush() {
            int lineStart = 0;

            for (int i = 0; i < byteCount; ++i) {
                byte b = byteBuffer[i];

                if (b == '\n') {
                    int j = i;
                    if (j > 0 && byteBuffer[j-1] == '\r') {
                        --j;
                    }

                    int lineLength = j - lineStart;
                    if (lineLength > 0) {
                        try {
                            String line = new String(byteBuffer, lineStart, 
                                                     lineLength, "UTF-8");
                            console.showMessage(line, messageType);
                        } catch(UnsupportedEncodingException cannotHappen) {}
                    }

                    lineStart = i+1;
                }
            }

            if (lineStart <= 0) {
                // No buffered '\n'.
                return;
            }

            int remain = byteCount - lineStart;
            if (remain > 0) {
                System.arraycopy(byteBuffer, lineStart, byteBuffer, 0, remain);
                byteCount = remain;
            } else {
                byteCount = 0;
            }
        }

        @Override
        public synchronized void close() {
            flush();

            if (byteCount > 0) {
                try {
                    String line = new String(byteBuffer, 0, byteCount, "UTF-8");
                    console.showMessage(line, messageType);
                } catch(UnsupportedEncodingException cannotHappen) {}
            }
        }
    }

    // -----------------------------------------------------------------------

    /**
     * Constructs an utility object allowing to redirect all 
     * the messages printed to <code>System.out</code> and 
     * <code>System.err</code> to specified {@link Console}.
     */
    public ConsoleCapture(Console console) {
        this.console = console;
    }
    
    /**
     * Start message redirection.
     *
     * @see #end
     */
    public void begin() {
        synchronized (nesting) {
            if (nesting[0] == 0) {
                systemOut = System.out;
                systemErr = System.err;

                try {
                    System.setOut(new PrintStream(
                        new OutputBuffer(console, Console.MessageType.INFO),
                        /*autoFlush*/ true, "UTF-8"));
                    System.setErr(new PrintStream(
                        new OutputBuffer(console, Console.MessageType.ERROR),
                        /*autoFlush*/ true, "UTF-8"));
                } catch(UnsupportedEncodingException cannotHappen) {}
            }
            ++nesting[0];
        }
    }

    /**
     * Stop message redirection and restore the real <code>System.out</code> 
     * and <code>System.err</code>.
     *
     * @see #begin
     */
    public void end() {
        synchronized (nesting) {
            if (nesting[0] > 0) {
                --nesting[0];

                if (nesting[0] == 0) {
                    System.out.close();
                    System.err.close();

                    System.setOut(systemOut);
                    System.setErr(systemErr);
                }
            }
        }
    }

    // -----------------------------------------------------------------------

    /*TEST_CONSOLE_CAPTURE
    public static void main(String[] args) 
        throws java.io.IOException {
        if (args.length != 2) {
            System.err.println(
                "usage: java ConsoleCapture in_text_file out_text_file");
            System.exit(1);
        }

        java.io.File inFile = new java.io.File(args[0]);
        java.io.File outFile = new java.io.File(args[1]);

        String input = FileUtil.loadString(inFile, "UTF-8");
        String[] records = StringUtil.split(input, '&');

        final java.io.PrintWriter output = 
            new java.io.PrintWriter(outFile, "UTF-8");
        
        ConsoleCapture capture = new ConsoleCapture(new Console() {
            public void showMessage(String message, MessageType messageType) {
                output.println(messageType + ": " + message);
                output.flush();
            }
        });

        try {
            capture.begin();
            capture.begin();
            capture.begin();

            for (int i = 0; i < records.length; ++i) {
                String record = records[i];

                if (i % 2 == 0) {
                    System.out.print(record);
                } else {
                    System.err.print(record);
                }
            }

            capture.end();
            capture.end();
            capture.end();
        } catch (Exception e) {
            e.printStackTrace(output);
            output.flush();
        }

        System.out.println("--> RESTORED System.out and System.err <--");

        output.flush();
        output.close();
    }
    TEST_CONSOLE_CAPTURE*/
}
