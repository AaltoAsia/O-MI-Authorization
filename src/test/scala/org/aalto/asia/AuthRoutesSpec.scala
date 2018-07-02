package org.aalto.asia

//#user-routes-spec
//#test-top
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2._
import org.specs2.matcher._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ write, read }

import database._
import requests._
import types.Path

class AuthRoutesSpec extends mutable.Specification with Specs2RouteTest
  with AuthRoutes {
  sequential

  val authDB = new AuthorizationDB()
  "AuthRoutes " >> {
    sequential
    "at start should" in {
      "return no users" in {
        "(GET /get-users)" in {
          // note that there's no need for the host part in the uri:
          val request = Get("/v1/get-users")

          request ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)

            // we expect the response to be json:
            contentType shouldEqual (ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[Set[String]] must be empty
          }
        }
        "(POST /get-users)" in {
          // note that there's no need for the host part in the uri:
          val request = Post("/v1/get-users")

          request ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)

            // we expect the response to be json:
            contentType shouldEqual (ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[Set[String]] must be empty
          }
        }
      }
      "return only initial groups" in {
        "(GET /get-groups)" in {
          // note that there's no need for the host part in the uri:
          val request = Get("/v1/get-groups")

          request ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)

            // we expect the response to be json:
            contentType shouldEqual (ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[Set[String]] shouldEqual (Set("DEFAULT", "ADMIN"))

          }
        }
        "(POST /get-groups)" in {
          // note that there's no need for the host part in the uri:
          val request = Post("/v1/get-groups")

          request ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)

            // we expect the response to be json:
            contentType shouldEqual (ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[Set[String]] shouldEqual (Set("DEFAULT", "ADMIN"))

          }
        }
      }
    }
    val username = "test_user"
    s"when adding new user $username should" in {
      "return OK with correct json" in {
        Post(
          "/v1/add-user",
          HttpEntity(ContentTypes.`application/json`, write(AddUser(username)))) ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)
          }
      }

      "make user shown when requesting all users" in {
        Post("/v1/get-users") ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)

          // we expect the response to be json:
          contentType shouldEqual (ContentTypes.`application/json`)

          // and no entries should be in the list:
          entityAs[Set[String]] must contain(username)
        }
      }
      s"create ${username}_USERGROUP group" in {
        Post("/v1/get-groups") ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)

          // we expect the response to be json:
          contentType shouldEqual (ContentTypes.`application/json`)

          // and no entries should be in the list:
          entityAs[Set[String]] must contain(s"${username}_USERGROUP")
        }
      }
      s"join it to DEFAULT and ${username}_USERGROUP automaticly" in {
        Post(
          "/v1/get-groups",
          HttpEntity(ContentTypes.`application/json`, write(GetGroups(Some(username))))) ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)

            // we expect the response to be json:
            contentType shouldEqual (ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[Set[String]] must contain(s"${username}_USERGROUP", "DEFAULT")
          }
      }
      s"make user show in get-users DEFAULT" in {
        Post(
          "/v1/get-users",
          HttpEntity(ContentTypes.`application/json`, write(GetUsers(Some("DEFAULT"))))) ~> routes ~> check {
            status shouldEqual (StatusCodes.OK)

            // we expect the response to be json:
            contentType shouldEqual (ContentTypes.`application/json`)

            // and no entries should be in the list:
            entityAs[Set[String]] must contain(username)
          }
      }
    }
    val groupname = "test_group"
    s"should create new group $groupname with /add-group $groupname" in {
      Post(
        "/v1/add-group",
        HttpEntity(ContentTypes.`application/json`, write(AddGroup(groupname)))) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)
        }
      Post("/v1/get-groups") ~> routes ~> check {
        status shouldEqual (StatusCodes.OK)

        // we expect the response to be json:
        contentType shouldEqual (ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[Set[String]] must contain(groupname)
      }
    }
    s"should join user $username to group $groupname with /join-groups" in {
      Post(
        "/v1/join-groups",
        HttpEntity(ContentTypes.`application/json`, write(JoinGroups(username, Set(groupname))))) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)
        }

      Post(
        "/v1/get-groups",
        HttpEntity(ContentTypes.`application/json`, write(GetGroups(Some(username))))) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)

          // we expect the response to be json:
          contentType shouldEqual (ContentTypes.`application/json`)

          // and no entries should be in the list:
          entityAs[Set[String]] must contain(groupname)
        }

      Post(
        "/v1/get-users",
        HttpEntity(ContentTypes.`application/json`, write(GetUsers(Some(groupname))))) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)

          // we expect the response to be json:
          contentType shouldEqual (ContentTypes.`application/json`)

          // and no entries should be in the list:
          entityAs[Set[String]] must contain(username)
        }
    }
    s"should let set new rules with /set-rules" >>{
      Post(
        "/v1/set-permissions",
        HttpEntity(ContentTypes.`application/json`, write(
          SetPermissions(
            groupname,
            Seq(
              Permission(Path("Objects","test"),ReadCall(),true),
              Permission(Path("Objects","test"),WriteDelete(),false)
            )
          )
        )
      )) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)
        }
      Post(
        "/v1/set-permissions",
        HttpEntity(ContentTypes.`application/json`, write(
          SetPermissions(
            "ADMIN",
            Seq(
              Permission(Path("Objects"),ReadWriteCallDelete(),true),
            )
          )
        )
      )) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)
        }
      Post(
        "/v1/set-permissions",
        HttpEntity(ContentTypes.`application/json`, write(
          SetPermissions(
            "DEFAULT",
            Seq(
              Permission(Path("Objects"),ReadCall(),true),
              Permission(Path("Objects"),WriteDelete(),false)
            )
          )
        )
      )) ~> routes ~> check {
          status shouldEqual (StatusCodes.OK)
        }
      Post(
        "/v1/get-permissions",
        HttpEntity(ContentTypes.`application/json`, write(
            GetPermissions(username,Read(),Set.empty)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual (StatusCodes.OK)

        // we expect the response to be json:
        contentType shouldEqual (ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[PermissionResult].allowed shouldEqual( Set(Path("Objects"),Path("Objects","test")))
        entityAs[PermissionResult].denied must be empty
      }
      Post(
        "/v1/get-permissions",
        HttpEntity(ContentTypes.`application/json`, write(
            GetPermissions(username,Write(),Set.empty)
          )
        )
      ) ~> routes ~> check {
        status shouldEqual (StatusCodes.OK)

        // we expect the response to be json:
        contentType shouldEqual (ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[PermissionResult].allowed shouldEqual( Set.empty[Path])
        entityAs[PermissionResult].denied shouldEqual( Set(Path("Objects","test")))
      }

    }
  }
}
