O-MI Authorization Service
==========================

Standalone server that implements O-MI Node reference implementation authorization protocol v2.
This service needs one form of authentication that should be configured in some other service, as explained in the guide.


Configuration
--------------

### DB

Slick configuration in application.conf

### O-MI Node

Set `omi-service.authAPI.v2.authorization-url`:
```
  authAPI.v2 {
    enable = true

    # Url to do authentication (checking if the consumer have valid credentials or session)
    authentication-url = "<set for authentication>"

    # Url to do authorization (checking what data a given user has permissions to read or write)
    authorization-url = "http://localhost:8001/auth"
  }
```

Compiling
----------

<!-- 2. Run tests: `sbt test`-->
1. [Install sbt](https://www.scala-sbt.org/1.0/docs/Setup.html)
3. run or package
  - Run: `sbt run`
  - Package: `sbt universal:packageBin` (zip) **or** `sbt universal:packageZipTarball` (tar)


