# DHT
--
A chord based distributed hash table (DHT) built using Android, which serves as the distributed system. All node joins are handled in real-time and position in the rings are alloted by the first avd, avd0. Built as a part of the CSE 586 Distributed Computers course at University at Buffalo.

 ## Usage
 - Create the 5 AVDs using the create_avd.py script: **python2.7 create_avd.py 5 /PATH_TO_SDK/**
 - Run the 5 AVDs using the run_avd.py script: **python2.7 run_avd.py 5**
 - Run the port redirection script using: **python2.7 set_redir.py 10000**
 - Now, run the grading script as follows: **./simpledht-grading.linux app-debug.apk**
 - The simpledht-grading.linux script is present in app/build/outputs so give the path accordingly

 For more information about the grading script, **run ./simpledht-grading.linux -h_**
  
 ## Implementation
  - Implemented three things: 
    - ID space partitioning/re-partitioning
    - Ring-based routing
    - Node joins.
  - The content provider implements all DHT functionalities and supports insert and query operations. Thus, if you run multiple instances of your app, all content provider instances should form a Chord ring and serve insert/query requests in a distributed fashion according to the Chord protocol.
  - The implementation details of each method i.e. insert(), delete(), query() and serverQuery() can be found in the code.
