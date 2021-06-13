package com.example.demo;

import com.netflix.graphql.dgs.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@DgsComponent
@RequiredArgsConstructor
@Validated
public class PostsDataFetcher {
    private final PostService postService;
    private final AuthorService authorService;

    @DgsQuery
    public List<Post> allPosts() {
        return this.postService.getAllPosts();
    }

    @DgsQuery
    public Post postById(@InputArgument String postId) {
        return this.postService.getPostById(postId).orElseThrow(() -> new PostNotFoundException(postId));
    }

    @DgsData(parentType = "Post", field = "author")
    public Author author(DgsDataFetchingEnvironment dfe) {
        Post post = dfe.getSource();
        String authorId = post.getAuthorId();
        return this.authorService.getAuthorById(authorId)
                .orElseThrow(() -> new AuthorNotFoundException(authorId));
    }

    @DgsMutation
    public Post createPost(@InputArgument("createPostInput") @Valid CreatePostInput input) {
        return this.postService.createPost(input);
    }

    @DgsMutation
    public Comment addComment(@InputArgument("commentInput") @Valid CommentInput input) {
        var comment = this.postService.addComment(input);
        this.emitters.forEach(e -> {
            try {
                e.send(comment, MediaType.APPLICATION_JSON);
            } catch (IOException ex) {
                e.completeWithError(ex);
            }
        });
        return comment;
    }

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @DgsSubscription
    public SseEmitter commentAdded(DgsDataFetchingEnvironment dfe) {
        SseEmitter emitter = new SseEmitter();
        // SseEmitter emitter = new SseEmitter(180_000L);

        this.emitters.add(emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));

        return emitter;
    }
}
