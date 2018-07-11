Example usage
-------------

Examples with [httpie](https://httpie.org/doc) program

### Adding users and groups

`http POST :8001/v1/add-user username=Tester1`

`http POST :8001/v1/add-group groupname=Testers`

### Joining and leaving groups

`http POST :8001/v1/join-groups username=Tester1 groups:='["Testers"]'`

`http POST :8001/v1/leave-groups username=Tester1 groups:='["Testers"]'`

### Setting and removing permissions 

`http POST :8001/v1/set-permissions group=Testers permissions:='[{"path":"Objects","request":"r","allow":true},{"path":"Objects","request":"wcd","allow":false}]'`

`http POST :8001/v1/remove-permissions group=Testers permissions:='[{"path":"Objects","allow":true}]'` 

### Getting permissions for request and user

`http POST :8001/v1/get-permissions username=Tester1 request=r`

Can also include groups known by authentication

`http POST :8001/v1/get-permissions username=Tester1 request=r groups:='["Testers"]'`

### Removing groups and user

`http POST :8001/v1/remove-group groupname=Testers`

`http POST :8001/v1/remove-user username=Tester1`

### Get all users

`http GET :8001/v1/get-users`

`http POST :8001/v1/get-users`

### Get users in group

`http GET :8001/v1/get-users groupname==Testers`

`http POST :8001/v1/get-users groupname=Testers`

Alternative path

`http POST :8001/v1/get-members groupname=Testers`

`http GET :8001/v1/get-members groupname==Testers`

### Get all groups

`http GET :8001/v1/get-groups`

`http POST :8001/v1/get-groups`

### Get all groups with given user as member

`http GET :8001/v1/get-groups username=Tester`

`http POST :8001/v1/get-groups username=Tester`

