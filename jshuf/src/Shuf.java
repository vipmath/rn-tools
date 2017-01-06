import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Shuf {
	
	public static void main(String[] args) throws IOException, InterruptedException {
		ArrayList<File> files = new ArrayList<File>();
		for (String filename : args) {
			File file = new File(filename);
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					if (f.getName().endsWith(".gz")
							||f.getName().endsWith(".txt")) {
						files.add(f);
					}
				}
			}
		}

		List<BufferedReader> ins = new ArrayList<>();
		Map<BufferedReader, String> inMap = new HashMap<>();
		for (File f : files) {
			if (f.getName().indexOf("_0") < 0) continue;//XXX
			if (f.getName().endsWith(".gz"))
				ins.add(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f)))));
			else if (f.getName().endsWith(".txt"))
				ins.add(new BufferedReader(new InputStreamReader(new FileInputStream(f))));
			else
				continue;
			System.out.println("read from " + f);
			inMap.put(ins.get(ins.size() - 1), f.getName());
		}

		System.out.println("read " + files.size());
		List<PrintStream> outs = new ArrayList<>();
		LinkedBlockingQueue<List<String>> freeQueue = new LinkedBlockingQueue<>();
		LinkedBlockingQueue<List<String>> writeQueue = new LinkedBlockingQueue<>();
//		for (int i = 0; i < 200; i++) {
		for (int i = 0; i < 20; i++) {
			System.out.println(String.format("shuf_%03d.gz", i));
//			outs.add(new PrintStream(new GZIPOutputStream(new FileOutputStream(new File(String.format("shuf_%03d.gz", i))))));
			outs.add(new PrintStream(new FileOutputStream(new File(String.format("shuf_%03d.txt", i)))));
//			outs.add(new PrintStream(new XZCompressorOutputStream(new FileOutputStream(new File(String.format("shuf_%03d.txt.xz", i))), 3)));
		}

		Thread[] threads = new Thread[4];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(() -> {
				Random r2 = new Random();
				while (true) {
					List<String> buf = writeQueue.poll();
					if (buf == null) {
						try {
//							if (Math.random() < 0.01)
//								System.out.print(".");
							Thread.sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					} else {
						if (buf.size() == 0)
							break;
						Collections.shuffle(buf);
						int n = r2.nextInt(outs.size());
						for (String line : buf) {
							if (line.indexOf("nan") >= 0)
								continue;
							if (!line.startsWith("|win ")) {
								System.err.println("broken line " + "::" + line);
								continue;
							}
							PrintStream out = outs.get(n % outs.size());
							synchronized (out) {
								out.println(line);
							}
							n ++;
						}
						buf.clear();
						freeQueue.add(buf);
					}
				}
			});
			threads[i].start();
		}

		int bufCap = 100000;
		List<String> buf = new ArrayList<>(bufCap);
		long num = 0;
		Random r = new Random();
		while (ins.size() > 0) {
			BufferedReader in = ins.get(r.nextInt(ins.size()));
			for (int j = 0; j < 1000; j++) {
				String line = in.readLine();
				if (line == null) {
					in.close();
					ins.remove(in);
					System.out.println("done " + ins.size() + " " + inMap.size());
					if (ins.size() == 0) break;
					if (ins.size() < 21) break;
					in = ins.get(r.nextInt(ins.size()));
					continue;
				}
				num++;
				if (num % (files.size() * 1000) == 0) {
					System.out.println(writeQueue.size() + " " + num / files.size());
				}
//				if (line.indexOf("nan") >= 0)
//					continue;
//				if (!line.startsWith("|win ")) {
//					System.err.println("broken line " + inMap.get(in) + "::" + line);
//					continue;
//				}
				//			int n = line.indexOf("|feature2");
				//			if (n > 0)
				//				out.println(line.substring(0, n));
				//			else
				buf.add(line);
			}
			if (buf.size() >= bufCap || ins.size() == 0) {
				writeQueue.add(buf);
				buf = freeQueue.poll();
				if (buf == null) {
					buf = new ArrayList<>(bufCap);
				}
			}
			if (writeQueue.size() > 10) {
				Thread.sleep(10);
				if (Math.random() < 0.01)
					System.out.print("-");
			}
		}

		for (int i = 0; i < threads.length; i++) {
			writeQueue.add(Collections.emptyList());
		}
		for (Thread thread : threads)
			thread.join();

		for (PrintStream out : outs) {
			out.close();
		}
	}
}
