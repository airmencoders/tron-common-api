package mil.tron.commonapi.security;

import mil.tron.commonapi.exception.AuthManagerException;
import mil.tron.commonapi.service.AppClientUserPreAuthenticatedService;
import mil.tron.commonapi.service.trace.TraceRequestFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;

@Configuration
@ConditionalOnProperty(name = "security.enabled", havingValue="true")
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	private TraceRequestFilter traceRequestFilter;

	private AppClientUserPreAuthenticatedService appClientUserService;
	public WebSecurityConfig(AppClientUserPreAuthenticatedService appClientUserService,
							 TraceRequestFilter traceRequestFilter) {
		this.appClientUserService = appClientUserService;
		this.traceRequestFilter = traceRequestFilter;
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
				.antMatchers("/actuator/httptrace").denyAll() // deny viewing http trace (have to look in db)
				.antMatchers("/actuator/health/**").hasAuthority("DASHBOARD_USER")
				.antMatchers("/actuator/logfile").hasAuthority("DASHBOARD_ADMIN")
				.antMatchers("/puckboard/**").hasAuthority("DASHBOARD_ADMIN")
	            .anyRequest()
	            	.authenticated()
            .and()
        	.cors()
        	.and()
            .csrf()
        		.disable()
        	.sessionManagement()
	        	.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			.and()
				.addFilterBefore(traceRequestFilter, ExceptionTranslationFilter.class)
			.headers()
			.contentSecurityPolicy("default-src 'self' 'unsafe-inline' 'unsafe-eval' *.dso.mil data:");
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

