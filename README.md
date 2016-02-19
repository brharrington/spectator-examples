
[![Build Status](https://travis-ci.org/brharrington/spectator-examples.svg)](https://travis-ci.org/brharrington/spectator-examples/builds)

Examples for using [Spectator](https://github.com/Netflix/spectator). Overview:

* [server](https://github.com/brharrington/spectator-examples/tree/master/server): is a small
  http server library instrumented using spectator.

To make this useful we need to setup bindings and report somewhere using one of the spectator
registry implementations. In the simplest cases that will be JMX or writing to local files:

* [servo](https://github.com/brharrington/spectator-examples/tree/master/servo): uses the server
  library example above and binds to the [Servo Registry](https://github.com/Netflix/spectator/wiki/Servo-Registry)
  for reporting to the file system.
* [metrics3](https://github.com/brharrington/spectator-examples/tree/master/servo): uses the server
  library example above and binds to the [Metrics3 Registry](https://github.com/Netflix/spectator/wiki/Metrics3-Registry)
  for reporting to JMX and to the file system.

