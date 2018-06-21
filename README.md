O-MI Authorization Service
==========================

Standalone server that implements O-MI Node reference implementation authorization protocol v2.
This service needs one form of authentication that should be configured in some other service.


Configuration
--------------

### O-MI Node


1. For now you need to compile O-MI Node from `feature_authapiv2` branch in O-MI Node (It will be released in near-future release).
2. In `application.conf`, set `omi-service.authAPI.v2.authorization-url` to and parameter object as below:
```
# This example is at root level, outside of any objects
omi-service.authAPI.v2 {
    enable = true

    # Url to do authentication (checking if the consumer have valid credentials or session)
    #authentication.url = "<set for authentication>"

    # Url to do authorization (checking what data a given user has permissions to read or write)
    authorization.url = "http://localhost:8001/v1/get-permissions"
    
    # predefined variables: requestType and requestTypeLetter which tell O-MI verb name (read, write, call, delete)
    # for O-MI Authorization ref. impl: http POST {"username": <username>, "request": <first-character-of-omi-request-type>}
    parameters.toAuthorization {
      # authorizationHeader {}
      # headers {}
      jsonbody {
        # jsonproperty = variableName
        username = "username"
        request = "requestTypeChar"
      }
    }

  }
```

### Authorization module

See configuration file `application.conf`.


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


Api docs
-------

* [Html API docs](http://aaltoasia.github.io/O-MI-Authorization/)
* [swagger.yaml](https://github.com/AaltoAsia/O-MI-Authorization/blob/master/swagger.yaml)

Example usage
-------------

Examples with [httpie](https://httpie.org/doc) program

`http POST :8001/v1/add-user username=Tester1`

`http POST :8001/v1/add-group groupname=Testers`

`http POST :8001/v1/join-groups username=Tester1 groups:='["Testers"]'`

`http POST :8001/v1/set-rules group=Testers rules:='[{"path":"Objects","request":"r","allow":true},{"path":"Objects","request":"wcd","allow":false}]'`

`http POST :8001/v1/get-permissions username=Tester1 request=r`

`http POST :8001/v1/remove-rules group=Testers rules:='[{"path":"Objects","allow":true}]'` 

`http POST :8001/v1/leave-groups username=Tester1 groups:='["Testers"]'`

`http POST :8001/v1/remove-group groupname=Testers`

`http POST :8001/v1/remove-user username=Tester1`
