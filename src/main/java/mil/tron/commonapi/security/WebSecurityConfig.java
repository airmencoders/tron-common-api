package mil.tron.commonapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
	
	@Value("${api-prefix.v1}")
	private String apiPrefix;
	
	private AppClientUserPreAuthenticatedService appClientUserService;
	
	public WebSecurityConfig(AppClientUserPreAuthenticatedService appClientUserService) {
		this.appClientUserService = appClientUserService;
	}
	
	@Override
	public void configure(AuthenticationManagerBuilder auth) throws Exception {
    	PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider = new PreAuthenticatedAuthenticationProvider();
    	preAuthenticatedAuthenticationProvider.setPreAuthenticatedUserDetailsService(appClientUserService);
    	auth.authenticationProvider(preAuthenticatedAuthenticationProvider);
	}
	
	@Override
    protected void configure(HttpSecurity http) throws Exception {
        http
        	.addFilter(appClientPreAuthFilter())
        	.authorizeRequests()
//	            .antMatchers(String.format("/%s/person", apiPrefix)).hasAuthority("ADMIN")
//	            .antMatchers(String.format("/%s/organization", apiPrefix)).hasAuthority("ORG")
            .anyRequest()
            	.authenticated()
            .and()
            .csrf()
        		.disable()
        	.sessionManagement()
	        	.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
    
	public AppClientPreAuthFilter appClientPreAuthFilter() throws Exception {
		AppClientPreAuthFilter filter = new AppClientPreAuthFilter();
		filter.setAuthenticationManager(authenticationManager());
		return filter;
	}
}
