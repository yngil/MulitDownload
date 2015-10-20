/**
 * Copyright(c) 2011-2015 by hexin Inc.
 * All Rights Reserved
 */
package com.study.mulitdownload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.collect.Lists;

/**
 * 
 *
 * @author spawnyngil@gmail.com
 */
public class DownLoader {

    private static File file;
    private String source;
    private String desc;
    private int threadNum = 3;
    private List<Future<File>> tasks = Lists.newArrayList();
    private ExecutorService executor = Executors.newFixedThreadPool(this.threadNum);
    private ExecutorCompletionService<File> completionService = new ExecutorCompletionService<File>(this.executor);

    public DownLoader(String _source, String _desc) throws Exception {
        this.source = _source;
        this.desc = _desc;
        this.down();
    }

    public void down() throws Exception {
        file = new File(this.desc);
        long length = this.getLength();
        for (int i = 0, n = this.threadNum; i < n; i++) {
            long start = i == 0 ? i * (length / n) : i * (length / n) + 1, end = n == 1 || i + 1 < n ? (length / n)
                    * (i + 1) : (length % n) * (i + 1);
            DownLoadThread thread = new DownLoadThread(start, end, this.source, this.desc, "线程" + (i + 1));
            this.tasks.add(this.completionService.submit(thread));
        }
        try {
            if (!this.hasDone()) {
                throw new Exception("下载异常.");
            }
        } finally {
            this.close();
        }
        System.out.println("下载完成.");
    }

    public long getLength() throws IOException {
        HttpURLConnection conn = null;
        URL url = new URL(this.source);
        conn = (HttpURLConnection) url.openConnection();
        return conn.getContentLengthLong();
    }

    public boolean hasDone() {
        for (int i = 0, n = this.tasks.size(); i < n;) {
            Future<File> future = null;
            try {
                future = this.completionService.take();
                future.get();
            } catch (InterruptedException e) {
                return false;
            } catch (ExecutionException e) {
                return false;
            }
            if (future.isDone()) {
                i++;
            }
        }
        return true;
    }

    public void close() {
        this.executor.shutdown();
    }

    public static class DownLoadThread implements Callable<File> {
        private long start;
        private long end;
        private String source;
        private String desc;
        private final int timeout = 5000;
        private String name;

        public DownLoadThread(long _start, long _end, String _source, String _desc, String _name) {
            this.start = _start;
            this.end = _end;
            this.source = _source;
            this.desc = _desc;
            this.name = _name;
        }

        public void download() throws Exception {
            HttpURLConnection conn = null;
            try {
                conn = this.getConnection();
                this._download(conn.getInputStream());
            } catch (Exception e) {
                throw e;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        private HttpURLConnection getConnection() throws Exception {
            HttpURLConnection conn = null;
            URL url = new URL(this.source);
            conn = (HttpURLConnection) url.openConnection();
            String range = String.format("bytes=%d-%d", this.start, this.end);
            conn.setConnectTimeout(this.timeout);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", range);
            return conn;
        }

        private void _download(InputStream _is) throws Exception {
            RandomAccessFile io = null;
            InputStream is = _is;
            try {
                io = new RandomAccessFile(DownLoader.file, "rw");
                byte b[] = new byte[4096];
                int offset = 0;
                System.out.println(this.name + "开始下载...");
                while ((offset = is.read(b, 0, 4096)) != -1) {
                    io.write(b, 0, offset);
                    System.out.println(this.name + "写入" + offset);
                    b = new byte[4096];
                }
                System.out.println(this.name + "下载完毕");
            } catch (IOException e) {
                throw e;
            } finally {
                if (io != null) {
                    io.close();
                }
                if (is != null) {
                    is.close();
                }
            }
        }

        @Override
        public File call() throws Exception {
            this.download();
            return DownLoader.file;
        }
    }

    public static void main(String[] args) {
        String source = "http://i1.hdslb.com/u_user/cf23a605e48673d319168cdf612942ec.png";
        String desc = "/Users/dns/Download/1.png";
        try {
            DownLoader download = new DownLoader(source, desc);
        } catch (IOException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
