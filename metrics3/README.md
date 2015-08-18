
Example for using the [Spectator Metrics3 Registry](https://github.com/Netflix/spectator/wiki/Metrics3-Registry).

To run the example:

```bash
$ git clone git@github.com:brharrington/spectator-examples.git
$ cd spectator-examples
$ ./gradlew metrics3:runMain
```

Then generate some load:

```bash
$ ab -c 10 -n 10000 http://localhost:54321/
$ curl -s 'http://localhost:54321/'
$ curl -s 'http://localhost:54321/' -HUser-Agent:chrome
```

Look at metrics using JMX or on the disk:

```bash
$ ls metrics3/build/metrics/
```
