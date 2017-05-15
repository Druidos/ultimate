#!/usr/bin/perl -i
#
# In the bash shell you can apply this script to all files in the folder using the
# for i in *.smt2 ; do perl THIS_SCRIPT.pl $i; done

while (<>) {
  if (/^\(set-logic .*/) {
     print '(set-info :smt-lib-version 2.5)
(set-logic ALL)
(set-info :source |
Generated by a component of the Ultimate program analysis framework [1] 
that implements a constraint-based synthesis of invariants [2].

This SMT script belongs to a set of SMT scripts that was generated by 
applying Ultimate to benchmarks [3] from the SV-COMP 2017 [4,5].

This script might _not_ contain all SMT commands that are used by 
Ultimate . In order to satisfy the restrictions of
the SMT-COMP we have to drop e.g., the commands for getting
values (resp. models), unsatisfiable cores and interpolants.

2017-05-01, Matthias Heizmann (heizmann@informatik.uni-freiburg.de)


[1] https://ultimate.informatik.uni-freiburg.de/
[2] Michael Colon, Sriram Sankaranarayanan, Henny Sipma: Linear Invariant 
Generation Using Non-linear Constraint Solving. CAV 2003: 420-432
[3] https://github.com/sosy-lab/sv-benchmarks
[4] Dirk Beyer: Software Verification with Validation of Results - 
(Report on SV-COMP 2017). TACAS (2) 2017: 331-349
[5] https://sv-comp.sosy-lab.org/2017/
|)
(set-info :license "https://creativecommons.org/licenses/by/4.0/")
(set-info :category "industrial")
(set-info :status unknown)
';
  } else {
    print $_;
  }
}