package dev.matheuscruz.quiff;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@WithTestResource(TempRepoResource.class)
class CompareResourceTest {

    @Test
    void refs_list_branches_and_default_base() {
        given().get("/api/refs").then().statusCode(200)
                .body("current", is("feature"))
                .body("defaultBase", is("main"))
                .body("branches", hasItem("feature"));
    }

    @Test
    void worktree_includes_commits_staged_unstaged_and_untracked() {
        given().queryParam("base", "main").get("/api/compare").then().statusCode(200)
                .body("commits", hasSize(1))
                .body("commits[0].subject", is("add feature"))
                .body("diff", allOf(
                        containsString("+two staged"),
                        containsString("+feature unstaged"),
                        containsString("+++ b/new.txt")));
    }

    @Test
    void branch_compare_ignores_uncommitted_changes() {
        given().queryParam("base", "main").queryParam("head", "feature").get("/api/compare").then().statusCode(200)
                .body("diff", allOf(containsString("+feature"), not(containsString("staged")),
                        not(containsString("new.txt"))));
    }

    @Test
    void rejects_option_like_refs() {
        given().queryParam("base", "--output=/tmp/x").get("/api/compare").then().statusCode(400);
        given().queryParam("base", "does-not-exist").get("/api/compare").then().statusCode(400);
    }
}
