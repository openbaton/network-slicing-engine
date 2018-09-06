package org.openbaton.nse.api;

/**
 * Created by lgr on 2/1/18.
 */
import org.openbaton.nse.properties.NseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import org.springframework.web.cors.CorsUtils;

@Configuration
public class ApiSecurity extends WebSecurityConfigurerAdapter {

  @Autowired private NseProperties nse_configuration;

  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication()
        .withUser(nse_configuration.getUser())
        .password(nse_configuration.getPassword())
        .roles("USER", "ADMIN");
  }

  protected void configure(HttpSecurity http) throws Exception {
    http.authorizeRequests()
        .requestMatchers(CorsUtils::isPreFlightRequest)
        .permitAll()
        .anyRequest()
        .authenticated()
        .and()
        .httpBasic();
  }
}
