/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapred;

import java.io.IOException;

import org.apache.hadoop.ipc.VersionedProtocol;

/** 
 * Protocol that a JobClient and the central JobTracker use to communicate.  The
 * JobClient can use these methods to submit a Job for execution, and learn about
 * the current system status.
 */ 
public interface JobSubmissionProtocol extends VersionedProtocol {
  /* 
   *Changing the versionID to 2L since the getTaskCompletionEvents method has
   *changed.
   *Changed to 4 since killTask(String,boolean) is added
   *Version 4: added jobtracker state to ClusterStatus
   *Version 5: max_tasks in ClusterStatus is replaced by
   * max_map_tasks and max_reduce_tasks for HADOOP-1274
   * Version 6: change the counters representation for HADOOP-2248
   * Version 7: added getAllJobs for HADOOP-2487
   * Version 8: change {job|task}id's to use corresponding objects rather that strings.
   * Version 9: change the counter representation for HADOOP-1915
   * Version 10: added getSystemDir for HADOOP-3135
   */
  public static final long versionID = 10L;

  /**
   * Allocate a name for the job.
   * @return a unique job name for submitting jobs.
   * @throws IOException
   */
  public JobID getNewJobId() throws IOException;

  /**
   * Submit a Job for execution.  Returns the latest profile for
   * that job.
   * The job files should be submitted in <b>system-dir</b>/<b>jobName</b>.
   */
  public JobStatus submitJob(JobID jobName) throws IOException;

  /**
   * Get the current status of the cluster
   * @return summary of the state of the cluster
   */
  public ClusterStatus getClusterStatus() throws IOException;
    
  /**
   * Kill the indicated job
   */
  public void killJob(JobID jobid) throws IOException;

  /**
   * Kill indicated task attempt.
   * @param taskId the id of the task to kill.
   * @param shouldFail if true the task is failed and added to failed tasks list, otherwise
   * it is just killed, w/o affecting job failure status.  
   */ 
  public boolean killTask(TaskAttemptID taskId, boolean shouldFail) throws IOException;
  
  /**
   * Grab a handle to a job that is already known to the JobTracker.
   * @return Profile of the job, or null if not found. 
   */
  public JobProfile getJobProfile(JobID jobid) throws IOException;

  /**
   * Grab a handle to a job that is already known to the JobTracker.
   * @return Status of the job, or null if not found.
   */
  public JobStatus getJobStatus(JobID jobid) throws IOException;

  /**
   * Grab the current job counters
   */
  public Counters getJobCounters(JobID jobid) throws IOException;
    
  /**
   * Grab a bunch of info on the map tasks that make up the job
   */
  public TaskReport[] getMapTaskReports(JobID jobid) throws IOException;

  /**
   * Grab a bunch of info on the reduce tasks that make up the job
   */
  public TaskReport[] getReduceTaskReports(JobID jobid) throws IOException;

  /**
   * A MapReduce system always operates on a single filesystem.  This 
   * function returns the fs name.  ('local' if the localfs; 'addr:port' 
   * if dfs).  The client can then copy files into the right locations 
   * prior to submitting the job.
   */
  public String getFilesystemName() throws IOException;

  /** 
   * Get the jobs that are not completed and not failed
   * @return array of JobStatus for the running/to-be-run
   * jobs.
   */
  public JobStatus[] jobsToComplete() throws IOException;
    
  /** 
   * Get all the jobs submitted. 
   * @return array of JobStatus for the submitted jobs
   */
  public JobStatus[] getAllJobs() throws IOException;
  
  /**
   * Get the diagnostics for a given task in a given job
   * @param taskId the id of the task
   * @return an array of the diagnostic messages
   */
  public String[] getTaskDiagnostics(TaskAttemptID taskId) 
  throws IOException;

  /**
   * Grab the jobtracker system directory path where job-specific files are to be placed.
   * 
   * @return the system directory where job-specific files are to be placed.
   */
  public String getSystemDir();  

}
