package com.talentmatch;

import com.talentmatch.model.User;
import com.talentmatch.repository.JobApplicationRepository;
import com.talentmatch.repository.JobRepository;
import com.talentmatch.repository.ProjectRepository;
import com.talentmatch.repository.QuoteRevisionRepository;
import com.talentmatch.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SmokeTestFixtures.class)
class SmokeCrawlTest {
    private static final Pattern VARIABLE = Pattern.compile("\\{([^/}:]+)(?::[^}]*)?}");

    @Autowired private MockMvc mockMvc;
    @Autowired private RequestMappingHandlerMapping handlerMapping;
    @Autowired private UserRepository users;
    @Autowired private JobRepository jobs;
    @Autowired private JobApplicationRepository applications;
    @Autowired private ProjectRepository projects;
    @Autowired private QuoteRevisionRepository quotes;

    @Test
    @WithUserDetails("client@smoke.test")
    void crawlGetRoutesAsCustomer() throws Exception {
        crawl("CUSTOMER", true);
    }

    @Test
    @WithUserDetails("freelancer@smoke.test")
    void crawlGetRoutesAsFreelancer() throws Exception {
        crawl("FREELANCER", true);
    }

    @Test
    @WithUserDetails("admin@smoke.test")
    void crawlGetRoutesAsAdmin() throws Exception {
        crawl("ADMIN", true);
    }

    @Test
    void crawlGetRoutesAsAnonymous() throws Exception {
        crawl("ANONYMOUS", false);
    }

    private void crawl(String role, boolean authenticated) throws Exception {
        List<RouteResult> results = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            if (entry.getKey().getMethodsCondition().getMethods().stream()
                    .anyMatch(method -> !method.name().equals("GET") && !method.name().equals("HEAD"))) continue;
            for (String pattern : entry.getKey().getPatternValues()) {
                if (pattern.equals("/error") || pattern.startsWith("/error/")) continue;
                String path = concretePath(pattern);
                MvcResult result;
                try {
                    result = mockMvc.perform(get(path)).andReturn();
                    results.add(new RouteResult(pattern, path, result.getResponse().getStatus(),
                            rootException(result.getResolvedException()), result.getResponse().getRedirectedUrl()));
                } catch (Exception exception) {
                    results.add(new RouteResult(pattern, path, 500, rootException(exception), null));
                }
            }
        }

        results.sort(Comparator.comparing(RouteResult::pattern).thenComparing(RouteResult::path));
        System.out.printf("%nGET route crawl | role=%s | route | status | root exception%n", role);
        for (RouteResult result : results) {
            System.out.printf("%s | %s | %d | %s%n", result.pattern(), result.path(), result.status(),
                    result.rootException());
        }

        List<RouteResult> serverErrors = results.stream().filter(result -> result.status() >= 500).toList();
        assertTrue(serverErrors.isEmpty(), role + " GET routes returned 5xx: " + serverErrors);
        if (!authenticated) {
            for (RouteResult result : results) {
                if (isProtected(result.pattern())) {
                    assertTrue(result.status() == 302 || result.status() == 303,
                            "Anonymous protected route must redirect to login: " + result);
                    assertTrue(result.redirect() != null && isLoginRedirect(result.redirect()),
                            "Anonymous protected route must target the login page: " + result);
                }
            }
        }
    }

    private String concretePath(String pattern) {
        Matcher matcher = VARIABLE.matcher(pattern);
        StringBuffer concrete = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(concrete,
                Matcher.quoteReplacement(valueFor(matcher.group(1))));
        matcher.appendTail(concrete);
        return concrete.toString().replace("/**", "/");
    }

    private String valueFor(String name) {
        User customer = users.findByEmailIgnoreCase("client@smoke.test").orElseThrow();
        User freelancer = users.findByEmailIgnoreCase("freelancer@smoke.test").orElseThrow();
        Long jobId = jobs.findByCustomerOrderByCreatedAtDesc(customer).stream().findFirst()
                .map(job -> job.getId()).orElse(1L);
        Long applicationId = applications.findByJobOrderByCreatedAtDesc(
                        jobs.findByCustomerOrderByCreatedAtDesc(customer).stream().findFirst().orElseThrow())
                .stream().findFirst().map(application -> application.getId()).orElse(1L);
        Long projectId = projects.findByCustomerOrderByCreatedAtDesc(customer).stream().findFirst()
                .map(project -> project.getId()).orElse(1L);
        Long revisionId = quotes.findAll().stream().findFirst().map(quote -> quote.getId()).orElse(1L);
        return switch (name.toLowerCase()) {
            case "jobid" -> jobId.toString();
            case "applicationid" -> applicationId.toString();
            case "freelancerid", "userid" -> freelancer.getId().toString();
            case "projectid" -> projectId.toString();
            case "revisionid", "quoteid", "id" -> revisionId.toString();
            case "type", "tab", "status", "kind", "role" -> "all";
            default -> "1";
        };
    }

    private boolean isProtected(String pattern) {
        return pattern.startsWith("/customer/") || pattern.startsWith("/freelancer/")
                || pattern.startsWith("/admin/") || pattern.startsWith("/projects/")
                || pattern.startsWith("/payment/project/") || pattern.startsWith("/notifications")
                || pattern.startsWith("/jobs/") && pattern.endsWith("/document");
    }

    private boolean isLoginRedirect(String location) {
        return location.contains("/login");
    }

    private String rootException(Throwable exception) {
        if (exception == null) return "—";
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }

    private record RouteResult(String pattern, String path, int status, String rootException, String redirect) {}
}
