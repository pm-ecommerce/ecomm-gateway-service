package com.pm.ecommerce.gateway_service.filters;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.pm.ecommerce.entities.Employee;
import com.pm.ecommerce.entities.Permission;
import com.pm.ecommerce.entities.Role;
import com.pm.ecommerce.gateway_service.repositories.EmployeeRepository;
import com.pm.ecommerce.gateway_service.repositories.RoleRepository;
import com.pm.ecommerce.gateway_service.services.JWTUtil;
import io.jsonwebtoken.Claims;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Configuration
public class RequestFilter extends ZuulFilter {
    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JWTUtil jwtUtil;

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    private boolean isMethodMatch(Permission permission, String method) {
        String action = permission.getAction().toLowerCase();

        if (action.equals("any") || action.equals("*")) {
            return true;
        }

        if (method.equals("options") || method.equals("head") || method.equals("request")) {
            return true;
        }

        return method.toLowerCase().equals(action);
    }

    private boolean isAllowedServiceAccess(String service, String type) {
        switch (type) {
            case "user":
            case "guest":
                return service.equals("pm-accounts")
                        || service.equals("pm-search")
                        || service.equals("pm-shopping-cart")
                        || service.equals("pm-orders");

            case "vendor":
                return service.equals("pm-accounts")
                        || service.equals("pm-orders")
                        || service.equals("pm-reports")
                        || service.equals("pm-products");
        }
        return true;
    }

    private void blockUser(RequestContext requestContext) {
        requestContext.setSendZuulResponse(false);
        requestContext.setResponseBody("Not authorized");
        requestContext.getResponse().setHeader("Content-Type", "text/plain;charset=UTF-8");
        requestContext.setResponseStatusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @SneakyThrows
    @Override
    public Object run() {
        RequestContext requestContext = RequestContext.getCurrentContext();
        try {
            HttpServletRequest httpServletRequest = requestContext.getRequest();
            final String authorizationHeader = httpServletRequest.getHeader("Authorization");
            String jwt;
            boolean allowedRequest = false;
            String type = "guest";
            int id = 0;

            String requestPath = httpServletRequest.getServletPath().substring(1);
            String serviceName = requestPath.substring(0, requestPath.indexOf("/"));

            if (requestPath.contains("login")) {
                System.out.println("Allow login to " + type);
                return null;
            }

            if (authorizationHeader != null) {
                jwt = authorizationHeader;
                Claims claims = jwtUtil.extractAllClaims(jwt);
                type = ((String) claims.get("type")).toLowerCase();
                id = (int) claims.get("id");
            }

            if (!isAllowedServiceAccess(serviceName, type)) {
                blockUser(requestContext);
                System.out.println("Blocking access to service " + serviceName + " for " + type);
                return null;
            }

            Role role = null;
            if (type.equals("employee")) {
                Employee employee = employeeRepository.findById(id).orElse(null);
                if (employee != null)
                    role = employee.getRole();
                else {
                    type = "guest";
                }
            }


            if (role == null) {
                role = roleRepository.findRoleByName(type);
            }

            if (type.equals("user") && role == null) {
                role = roleRepository.findRoleByName("guest");
            }

            System.out.println("Detected type: " + type);
            if (role != null) {
                System.out.println("Detected role: " + role.getName());
                List<Permission> permissions = role.getPermissions();
                if (!type.equals("guest")) {
                    Role role1 = roleRepository.findRoleByName("guest");
                    if (role1 != null && role1.getPermissions().size() > 0) {
                        List<Permission> permissions1 = role1.getPermissions();
                        permissions.addAll(permissions1);
                    }
                }

                System.out.println("Total permissions: " + permissions.size());
                for (Permission permission : permissions) {
                    String path = httpServletRequest.getServletPath();
                    path = path.substring(1);
                    path = path.substring(path.indexOf("/"));

                    String method = httpServletRequest.getMethod();
                    System.out.println("Matching: " + permission.getPath() + " with " + path);
                    if (permission.getPath().equals("*") || permission.getPath().equals("/")) {
                        System.out.println("Checking method permission: " + permission.getAction() + " with " + method);
                        allowedRequest = isMethodMatch(permission, method);
                        break;
                    }

                    String[] paths = path.split("/");
                    String[] allowedPaths = permission.getPath().split("/");

                    if (paths.length != allowedPaths.length) {
                        continue;
                    }

                    boolean pathMatched = true;
                    for (int i = 0; i < paths.length; i++) {
                        if (allowedPaths[i].contains("{") && allowedPaths[i].contains("}")) {
                            continue;
                        }
                        if (!allowedPaths[i].equals(paths[i])) {
                            pathMatched = false;
                            break;
                        }
                    }

                    if (pathMatched) {
                        allowedRequest = isMethodMatch(permission, method);
                        if (allowedRequest) {
                            break;
                        }
                    }
                }
            }

            if (!allowedRequest) {
                blockUser(requestContext);
            }
        } catch (Exception e) {
            blockUser(requestContext);
            System.out.println(e.getMessage());
        }

        return null;
    }
}
