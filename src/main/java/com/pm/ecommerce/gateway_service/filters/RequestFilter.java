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
            case "guest":
                return service.equals("pm-accounts")
                        || service.equals("pm-search")
                        || service.equals("pm-shopping-cart");

            case "user":
                return service.equals("pm-accounts")
                        || service.equals("pm-search")
                        || service.equals("pm-shopping-cart")
                        || service.equals("orders");

            case "vendor":
                return service.equals("pm-accounts")
                        || service.equals("orders")
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

            if (authorizationHeader != null) {
                jwt = authorizationHeader;
                Claims claims = jwtUtil.extractAllClaims(jwt);
                type = (String) claims.get("type");
                id = (int) claims.get("id");
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

            String requestPath = httpServletRequest.getServletPath().substring(1);
            String serviceName = requestPath.substring(0, requestPath.indexOf("/"));

            if (!isAllowedServiceAccess(serviceName, type)) {
                blockUser(requestContext);
                return null;
            }


            if (role == null) {
                role = roleRepository.findRoleByName(type);
            }

            if (role != null) {
                List<Permission> permissions = role.getPermissions();
                if (!type.equals("guest")) {
                    Role role1 = roleRepository.findRoleByName("guest");
                    if (role1 != null && role1.getPermissions().size() > 0) {
                        List<Permission> permissions1 = role1.getPermissions();
                        permissions.addAll(permissions1);
                    }
                }

                for (Permission permission : permissions) {
                    String path = httpServletRequest.getServletPath();
                    path = path.substring(1);
                    path = path.substring(path.indexOf("/"));

                    String method = httpServletRequest.getMethod();

                    if (permission.getPath().equals("*")) {
                        allowedRequest = isMethodMatch(permission, method);
                        break;
                    }

                    String[] paths = path.split("/");
                    String[] allowedPaths = permission.getPath().split("/");

                    if (paths.length != allowedPaths.length) {
                        break;
                    }

                    boolean pathMatched = true;
                    for (int i = 0; i < paths.length; i++) {
                        if (allowedPaths[i].equals("*")) {
                            continue;
                        }
                        if (!allowedPaths[i].equals(paths[i])) {
                            pathMatched = false;
                            break;
                        }
                    }

                    if (pathMatched) {
                        allowedRequest = isMethodMatch(permission, method);
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
