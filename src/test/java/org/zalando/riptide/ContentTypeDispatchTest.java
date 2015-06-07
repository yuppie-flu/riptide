package org.zalando.riptide;

/*
 * ⁣​
 * riptide
 * ⁣⁣
 * Copyright (C) 2015 Zalando SE
 * ⁣⁣
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ​⁣
 */

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.zalando.riptide.Conditions.anyContentType;
import static org.zalando.riptide.Conditions.on;
import static org.zalando.riptide.Feature.feature;
import static org.zalando.riptide.MediaTypes.ERROR;
import static org.zalando.riptide.MediaTypes.PROBLEM;
import static org.zalando.riptide.MediaTypes.SUCCESS;
import static org.zalando.riptide.Selectors.contentType;

public final class ContentTypeDispatchTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private final URI url = URI.create("https://api.example.com");

    private final Rest unit;
    private final MockRestServiceServer server;

    public ContentTypeDispatchTest() {
        final RestTemplate template = new RestTemplate();
        template.setErrorHandler(new PassThroughResponseErrorHandler());
        this.server = MockRestServiceServer.createServer(template);
        this.unit = Rest.create(template);
    }

    private Success perform() {
        return unit.execute(GET, url)
                .dispatch(contentType(),
                        on(SUCCESS, Success.class).capture(),
                        on(PROBLEM, Problem.class).call(this::onProblem),
                        on(ERROR, Error.class).call(this::onError),
                        anyContentType().call(this::fail))
                .retrieve(Success.class).get();
    }

    @Test
    public void shouldDispatchSuccess() {
        server.expect(requestTo(url))
                .andRespond(withSuccess()
                        .body(new ClassPathResource("success.json"))
                        .contentType(SUCCESS));

        final Success success = perform();

        assertThat(success, is(happy()));
    }

    @Test
    public void shouldDispatchProblem() {
        server.expect(requestTo(url))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .body(new ClassPathResource("problem.json"))
                        .contentType(PROBLEM));

        exception.expect(ProblemException.class);
        exception.expect(feature(ProblemException::getProblem,
                feature(Problem::getType, is(URI.create("http://httpstatus.es/422"))),
                feature(Problem::getTitle, is("Unprocessable Entity")),
                feature(Problem::getStatus, is(422)),
                feature(Problem::getDetail, is("A problem occcured."))));

        perform();
    }

    @Test
    public void shouldDispatchError() {
        server.expect(requestTo(url))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .body(new ClassPathResource("error.json"))
                        .contentType(ERROR));

        exception.expect(ErrorException.class);
        exception.expect(feature(ErrorException::getError,
                feature(Error::getMessage, is("A problem occured.")),
                feature(Error::getPath, is(url))));

        perform();
    }

    private Matcher<Success> happy() {
        return feature(Success::isHappy, equalTo(true));
    }

    private void onProblem(Problem problem) {
        throw new ProblemException(problem);
    }

    private void onError(Error error) {
        throw new ErrorException(error);
    }

    private void fail(ClientHttpResponse response) {
        try {
            throw new AssertionError(response.getStatusText());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
