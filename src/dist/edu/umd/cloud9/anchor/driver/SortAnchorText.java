/*
 * Cloud9: A MapReduce Library for Hadoop
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package edu.umd.cloud9.anchor.driver;


import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.mapred.lib.IdentityMapper;
import org.apache.hadoop.mapred.lib.IdentityReducer;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.umd.cloud9.anchor.data.AnchorText;
import edu.umd.cloud9.io.array.ArrayListWritable;

/**
 * <p>
 * Main driver program for sorting the web graph. Command-line arguments are as follows:
 * </p>
 * 
 * <ul>
 * <li>[input-path]: the input web graph (, (weighted) inverse web graph, etc.)</li>
 * <li>[output-path]: the output path</li>
 * <li>[number-of-documents]: an estimate of the number of pages in the graph</li>
 * <li>[number-of-reducers]: number of reducers</li>
 * </ul>
 * 
 * @author Nima Asadi
 * 
 */

@SuppressWarnings("deprecation")
public class SortAnchorText extends Configured implements Tool {

	private static final Logger sLogger = Logger.getLogger(SortAnchorText.class);
	
	public static final int DEFAULT_NUMBER_OF_DOCUMENTS = 503903810;
	
	protected static class Partition implements Partitioner<IntWritable, ArrayListWritable<AnchorText>> {
		
		int totalDocuments;
		
		public void configure(JobConf job) {
			totalDocuments = job.getInt("Ivory.NumberOfDocuments", DEFAULT_NUMBER_OF_DOCUMENTS);
		}

		public int getPartition(IntWritable key, ArrayListWritable<AnchorText> value, int numReduceTasks) {
			int i = (key.get() / (totalDocuments / numReduceTasks));
			
			if(i >= numReduceTasks)
				i = numReduceTasks - 1;
			
			return i;
		}
	}
	
	private static int printUsage() {
		System.out.println("usage: [input-path] [output-path] [number-of-documents] [number-of-reducers]");
		ToolRunner.printGenericCommandUsage(System.out);
		return -1;
	}
	
	public int run(String[] args) throws Exception {
		
		if(args.length != 4) {
			printUsage();
			return -1;
		}

		JobConf conf = new JobConf(getConf(), SortAnchorText.class);
		FileSystem fs = FileSystem.get(conf);
		
		String inputPath = args[0];
		String outputPath = args[1];
		int numberOfDocuments = Integer.parseInt(args[2]);
		int numMappers = 1;
		int numReducers = Integer.parseInt(args[3]);
		
		conf.setJobName("SortAnchorText");
		conf.set("mapred.child.java.opts", "-Xmx4096m");
		conf.setInt("mapred.task.timeout", 60000000);
		
		if(numberOfDocuments == 0)
			numberOfDocuments = DEFAULT_NUMBER_OF_DOCUMENTS;
		
		conf.setInt("Ivory.NumberOfDocuments", numberOfDocuments);

		conf.setNumMapTasks(numMappers);
		conf.setNumReduceTasks(numReducers);

		conf.setMapperClass(IdentityMapper.class);
		conf.setPartitionerClass(Partition.class);
		conf.setReducerClass(IdentityReducer.class);

		conf.setOutputKeyClass(IntWritable.class);
		conf.setOutputValueClass(ArrayListWritable.class);
		
		conf.setMapOutputKeyClass(IntWritable.class);
		conf.setMapOutputValueClass(ArrayListWritable.class);

		conf.setInputFormat(SequenceFileInputFormat.class);
		conf.setOutputFormat(SequenceFileOutputFormat.class);

		SequenceFileOutputFormat.setCompressOutput(conf, true);
		SequenceFileOutputFormat.setOutputCompressionType(conf, SequenceFile.CompressionType.BLOCK);

		SequenceFileInputFormat.setInputPaths(conf, inputPath);
		FileOutputFormat.setOutputPath(conf, new Path(outputPath));

		sLogger.info("SortAnchorText");
		sLogger.info(" - input path: " + inputPath);
		sLogger.info(" - output path: " + outputPath);	
		sLogger.info(" - number of documents: " + conf.getInt("Ivory.NumberOfDocuments", DEFAULT_NUMBER_OF_DOCUMENTS));

		fs.delete(new Path(outputPath));
		JobClient.runJob(conf);

		return 0;
	}
	
	public static void main(String[] args) throws Exception {
		int res = ToolRunner.run(new Configuration(), new SortAnchorText(), args);
		System.exit(res);
	}

}
