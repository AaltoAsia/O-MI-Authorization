package org.aalto.asia

//#user-routes-spec
//#test-top
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2._

import database._
import requests._
import types.Path

class AuthRoutesSpec extends mutable.Specification with Specs2RouteTest
  with AuthRoutes {
  sequential

  def shouldBeOkJson = {
    status shouldEqual OK
    contentType shouldEqual `application/json`
  }

  val authDB = new AuthorizationDB()
  "AuthRoutes " >> {
    sequential
    "at start should" in {
      "return no users" in {
        "(GET /get-users)" in {
          Get("/v1/get-users") ~> routes ~> check {
            shouldBeOkJson
            entityAs[Set[String]] must be empty
          }
        }
        "(POST /get-users)" in {
          Post("/v1/get-users") ~> routes ~> check {
            shouldBeOkJson
            entityAs[Set[String]] must be empty
          }
        }
      }
      "return only initial groups" in {
        "(GET /get-groups)" in {
          Get("/v1/get-groups") ~> routes ~> check {
            shouldBeOkJson
            entityAs[Set[String]] shouldEqual Set("DEFAULT", "ADMIN")
          }
        }
        "(POST /get-groups)" in {
          Post("/v1/get-groups") ~> routes ~> check {
            shouldBeOkJson
            entityAs[Set[String]] shouldEqual (Set("DEFAULT", "ADMIN"))
          }
        }
      }
    }

    val username = "test_user"

    s"when adding new user $username should" in {
      "return OK with correct json" in {
        Post( "/v1/add-user", AddUser(username)) ~> routes ~> check {
            status shouldEqual OK
          }
      }

      "make user shown when requesting all users" in {
        Post("/v1/get-users") ~> routes ~> check {
          shouldBeOkJson
          entityAs[Set[String]] must contain(username)
        }
      }
      s"create ${username}_USERGROUP group" in {
        Post("/v1/get-groups") ~> routes ~> check {
          shouldBeOkJson
          entityAs[Set[String]] must contain(s"${username}_USERGROUP")
        }
      }
      s"join it to DEFAULT and ${username}_USERGROUP automaticly" in {
        Post("/v1/get-groups", GetGroups(Some(username))) ~> routes ~> check {
          shouldBeOkJson
          entityAs[Set[String]] must contain(s"${username}_USERGROUP", "DEFAULT")
        }
      }
      s"make user show in get-users DEFAULT" in {
        Post( "/v1/get-users", GetUsers(Some("DEFAULT"))) ~> routes ~> check {
          shouldBeOkJson
          entityAs[Set[String]] must contain(username)
        }
      }
    }

    val groupname = "test_group"

    s"should create new group $groupname with /add-group $groupname" in {
      Post("/v1/add-group", AddGroup(groupname)) ~> routes ~> check {
        status shouldEqual OK
      }
      Post("/v1/get-groups") ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] must contain(groupname)
      }
    }
    s"should join user $username to group $groupname with /join-groups" in {
      Post(
        "/v1/join-groups",
        JoinGroups(username, Set(groupname))) ~> routes ~> check {
          status shouldEqual OK
        }

      Post(
        "/v1/get-groups",
        GetGroups(Some(username))) ~> routes ~> check {
          shouldBeOkJson
          entityAs[Set[String]] must contain(groupname)
        }

      Post(
        "/v1/get-users",
        GetUsers(Some(groupname))) ~> routes ~> check {
          shouldBeOkJson
          entityAs[Set[String]] must contain(username)
        }
    }
    s"should let set new rules with /set-permissions" >>{
      Post(
        "/v1/set-permissions",
        SetPermissions(
          groupname,
          Seq(
            Permission(Path("Objects","test"),ReadCall(),true),
            Permission(Path("Objects","test"),WriteDelete(),false)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual OK
      }

      Post(
        "/v1/set-permissions",
        SetPermissions(
          "ADMIN",
          Seq(
            Permission(Path("Objects"),ReadWriteCallDelete(),true),
          )
        )
      ) ~> routes ~> check {
        status shouldEqual OK
      }

      Post(
        "/v1/set-permissions",
        SetPermissions(
          "DEFAULT",
          Seq(
            Permission(Path("Objects"),ReadCall(),true),
            Permission(Path("Objects"),WriteDelete(),false)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual OK
      }

      Post(
        "/v1/get-permissions",
        GetPermissions(username,Read(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual Set(Path("Objects"),Path("Objects","test"))
        entityAs[PermissionResult].denied must be empty
      }

      Post(
        "/v1/get-permissions",
        GetPermissions(username,Write(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set.empty[Path])
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }
    }
    "should return groups permissions when additional group is given to /get-permissions" in {
      Post(
        "/v1/get-permissions",
        GetPermissions(username,Write(),Set("ADMIN"))
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set(Path("Objects")))
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }
    }
    "should change existing rules with /set-permissions" in {
      Post(
        "/v1/set-permissions",
        SetPermissions(
          "DEFAULT",
          Seq(
            Permission(Path("Objects"),Read(),true),
            Permission(Path("Objects"),WriteCallDelete(),false)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual OK
      }
      Post(
        "/v1/get-permissions",
        GetPermissions(username,Read(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual Set(Path("Objects"),Path("Objects","test"))
        entityAs[PermissionResult].denied must be empty
      }
      Post(
        "/v1/get-permissions",
        GetPermissions(username,Call(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set(Path("Objects","test")))
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects")))
      }
      Post(
        "/v1/get-permissions",
        GetPermissions(username,Write(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set.empty[Path])
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }
      Post(
        "/v1/get-permissions",
        GetPermissions(username,requests.Delete(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set.empty[Path])
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }
    }
    s"should be able to join groups" in {
      Post(
        "/v1/join-groups",
        JoinGroups(username,Set("ADMIN"))
      ) ~> routes ~> check {
        shouldBeOkJson
      }
      Post(
        "/v1/get-users",
        GetUsers(Some("ADMIN"))
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] must contain(username)
      }
      Post(
        "/v1/get-permissions",
        GetPermissions(username,Write(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set(Path("Objects")))
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }
    }
    s"should be able leave groups" in {
      Post(
        "/v1/leave-groups",
        LeaveGroups(username,Set("ADMIN"))
      ) ~> routes ~> check {
        shouldBeOkJson
      }
      Post(
        "/v1/get-users",
        GetUsers(Some("ADMIN"))
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] must not contain(username)
      }
      Post(
        "/v1/get-permissions",
        GetPermissions(username,Write(),Set.empty)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set.empty[Path])
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }
    }
    s"should be able to remove groups" in {
      Post(
        "/v1/remove-group",
        RemoveGroup(groupname)
      ) ~> routes ~> check {
        shouldBeOkJson
      }
      Post(
        "/v1/get-groups",
        GetGroups(None)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] shouldEqual( Set("ADMIN","DEFAULT",s"${username}_USERGROUP"))
      }
      Post(
        "/v1/get-users",
        GetUsers(Some(groupname))
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] must be empty
      }
      Post(
        "/v1/get-permissions",
        GetPermissions("unknown",Write(),Set(groupname))
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[PermissionResult].allowed shouldEqual( Set.empty[Path])
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects")))
      }
    }
    s"should not be able to remove user groups" in {
      Post(
        "/v1/remove-group",
        RemoveGroup(s"${username}_USERGROUP")
      ) ~> routes ~> check {
        //TODO: Specify error message and Check it
        //println( entityAs[String] )
        shouldBeOkJson

      }
      Post(
        "/v1/get-groups",
        GetGroups(None)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] must contain( s"${username}_USERGROUP")
      }
    }
    s"should be able to remove users" in {
      Post(
        "/v1/remove-user",
        RemoveUser(username)
      ) ~> routes ~> check {
        shouldBeOkJson
      }
      Post(
        "/v1/get-users",
        GetUsers(None)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] must be empty
      }
      Post(
        "/v1/get-groups",
        GetGroups(None)
      ) ~> routes ~> check {
        shouldBeOkJson
        entityAs[Set[String]] shouldEqual( Set( "DEFAULT","ADMIN"))
      }

    }
  }
}
