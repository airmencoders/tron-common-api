package mil.tron.commonapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;

import mil.tron.commonapi.exception.AuthManagerException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;

@Configuration
@ConditionalOnProperty(name = "security.enabled", havingValue="true")
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
				.antMatchers("/").permitAll()  // for swagger redirect to work at root of api
				.antMatchers("/api-docs/**").permitAll()
        		.antMatchers("/api-docs**").permitAll()
				.antMatchers("/" + this.apiPrefix + "/list-request-headers").permitAll()
	            .anyRequest()
	            	.authenticated()
            .and()
        	.cors()
        	.and()
            .csrf()
        		.disable()
        	.sessionManagement()
	        	.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
    
	public AppClientPreAuthFilter appClientPreAuthFilter() throws AuthManagerException {
		AppClientPreAuthFilter filter = new AppClientPreAuthFilter();
		try {
			filter.setAuthenticationManager(authenticationManager());
		} catch (Exception ex) {
			throw new AuthManagerException(ex.getLocalizedMessage());
		}
		return filter;
	}
}

