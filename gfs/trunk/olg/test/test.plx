#!/usr/bin/env perl
use strict;
use IO::Handle;
use Test::Simple tests => 8;

my $JAR = "jol.new";
my $OLG = "../paxos.olg";

my @handles;


$SIG{ALRM}  = 'cleanup';

test_1();
test_2();
test_3();

print "\b";


###############################################################

sub test_3 {
  # start a paxos instance over 3 replicas
  my @pids = pipe_many(2);

  # yay, array smushing
  push @handles,@pids;

  # kill one of the reps 
  kill(9,$handles[2]);

  # make a request
  my ($p4,$handle) = request(10000,"continuous_synod.olg","123");

  my $rest = snatch_reply($handle);

  # check that is it passed.
  ok($rest =~ /XACT0/,"continuous xact");
  ok($rest =~ /passed/,"passed");

  # 
  my $second = snatch_reply($handle);
  ok($rest =~ /XACT0/,"still zero");
  ok($rest =~ /passed/,"passed");

  # kill it

  foreach (@pids) {
    kill(9,$_);
  }
  kill(9,$p4);
  # look, it shouldn't have to be like this, but it is.
  system("killall java");

}



sub test_2 {
  # start a paxos instance over 3 replicas
  my @pids = pipe_many(5);
  # yay, array smushing
  push @handles,@pids;

  # make a request
  my ($p4,$handle) = request(10000,"start_synod.olg","123");

  my $rest = snatch_reply($handle);

  # check that is it passed.
  ok($rest =~ /XACT123/,"6 replicas");
  ok($rest =~ /passed/,"passed");

  # kill it

  foreach (@pids) {
    kill(9,$_);
  }
  kill(9,$p4);
  # look, it shouldn't have to be like this, but it is.
  system("killall java");

}

sub test_1 {
  # start a paxos instance
  my $p1 = pipe_p(0,0);
  #my $p1 = pipe_many(0);
  push @handles,$p1;

  # make a request
  my ($p2,$handle) = request(10000,"start_synod.olg","123");

  my $rest = snatch_reply($handle);

  # check that is it passed.
  ok($rest =~ /XACT123/,"my xact");
  ok($rest =~ /passed/,"passed");

  # kill it
  kill(9,$p2);
  kill(9,$p1);
}

sub snatch_reply {
  my ($handle) = @_;
  
  alarm(15);
  my $rest;
  while (<$handle>) {
    if (/reply/) {
      $rest = $_;
      $rest .= <$handle>;
      $rest .= <$handle>;
      last;
    }
  }
  alarm(0);
  return $rest;
}


sub cleanup {
  foreach (@handles) {
    kill(9,$_);
  }
  ok( 1 == 0, "timed out");
  system("killall java");
  exit(1);
}

sub request {
  my ($master,$program,$req) = @_;
  $ENV{"PROGRAM"} = "paxos";
  $ENV{"ME"} = "10009";
  $ENV{"XACT"} = $req;
  $ENV{"MASTER"} = $master;
  my $pid = open(PIPE2,"java -jar $JAR 10009 $program 2>&1 |");
  return ($pid,*PIPE2);
}
  
 
sub pipe_many {
  my ($procs) = @_;

  my @pids;
  for (my $i = 0;  $i < $procs; $i++) {
    my $kidpid = fork();
    if ($kidpid) {
      push @pids,$kidpid;
    } else {

      $ENV{"ME"} = $i;
      my $port = $i + 10000;
      $ENV{"PROC"} = $procs;
      exec("java -jar $JAR $port $OLG 2> pxtst.$port.$$ >> /dev/null");
      # this will never happen.
      exit();
    }
  }

  my $port = 10000 + $procs;
  $ENV{"ME"} = $procs;
  $ENV{"PROC"} = $procs;
  my $pid = open(PIPE,"java -jar $JAR $port $OLG 2>&1 |");
  while (<PIPE>) {
    #print "OUT: $_\n";
    if (/CLOCK/) {
      last;
    }
  }
  push @pids,$pid;
  # give them a few seconds to spin up.
  sleep(4);
  return @pids;
}

sub pipe_p {
  my ($id,$procs) = @_;

  my $port = $id + 10000;
  $ENV{"ME"} = $id;
  $ENV{"PROC"} = $procs;
  my $pid = open(PIPE,"java -jar $JAR $port $OLG 2>&1 |");
  while (<PIPE>) {
    #print "OUT: $_\n";
    if (/CLOCK/) {
      last;
    }
  }
  return $pid;
}

sub spawn_p {
  my ($id,$procs) = @_;
  my $kidpid = fork();

  my $port = $id + 10000;
  $ENV{"ME"} = $id;
  $ENV{"PROC"} = $procs;
  if (!$kidpid) {
    system("java -jar $JAR $port $OLG");
  }
  return $kidpid;
}
