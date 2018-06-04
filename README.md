O-MI Authorization Service
==========================

Standalone server that implements O-MI Node reference implementation authorization protocol v2.
This service needs one form of authentication that should be configured in some other service, as explained in the guide.


Configuration
--------------

### O-MI Node

Set `omi-service.authAPI.v2.authorization-url`:
```
  authAPI.v2 {
    enable = true

    # Url to do authentication (checking if the consumer have valid credentials or session)
    authentication.url = "<set for authentication>"

    # Url to do authorization (checking what data a given user has permissions to read or write)
    authorization.url = "http://localhost:8001/auth"
  }
```

### DB

TODO: Slick configuration in application.conf

Compiling
----------

<!-- 2. Run tests: `sbt test`-->
1. [Install sbt](https://www.scala-sbt.org/1.0/docs/Setup.html)
3. run or package
  - Run: `sbt run`
  - Package: `sbt universal:packageBin` (zip) **or** `sbt universal:packageZipTarball` (tar)

Known issues
------------

Sometimes all tables are not created.

Example usage
-------------
`http POST :8001/add-user username=Tester1`

`http POST :8001/add-group groupname=Testers`

`http POST :8001/join-groups username=Tester1 groups:='["Testers"]'`

`http POST :8001/set-rules group=Testers rules:='[{"path":"Objects","request":"r","allow":true},{"path":"Objects","request":"wcd","allow":false}]'`

`http POST :8001/get-permissions username=Tester1 request=r`

`http POST :8001/remove-rules group=Testers rules:='[{"path":"Objects","allow":true}]'` 

`http POST :8001/leave-groups username=Tester1 groups:='["Testers"]'`

`http POST :8001/remove-group groupname=Testers`

`http POST :8001/remove-user username=Tester1`
