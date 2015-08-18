
Example for using the [Spectator Servo Registry](https://github.com/Netflix/spectator/wiki/Servo-Registry).

To run the example:

```bash
$ git clone git@github.com:brharrington/spectator-examples.git
$ cd spectator-examples
$ ./gradlew servo:runMain
```

Then generate some load:

```bash
$ ab -c 10 -n 10000 http://localhost:54321/
$ curl -s 'http://localhost:54321/'
$ curl -s 'http://localhost:54321/' -HUser-Agent:chrome
```

Look at metrics on the disk:

```bash
$ ls servo/build/metrics/
```
