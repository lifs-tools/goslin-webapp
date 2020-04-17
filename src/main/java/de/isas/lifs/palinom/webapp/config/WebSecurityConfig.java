/*
 * Copyright 2018 nils.hoffmann.
 *
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
 */
package de.isas.lifs.palinom.webapp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.LdapShaPasswordEncoder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 *
 * @author nils.hoffmann
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Value("${spring.ldap.urls}")
    private String ldapUrls;
    @Value("${spring.ldap.base}")
    private String ldapBaseDn;
    @Value("${spring.ldap.username}")
    private String ldapManagerDn;
    @Value("${spring.ldap.password}")
    private String ldapManagerPassword;
    @Value("${lifs.auth.ldap.user.dn.patterns}")
    private String ldapUserDnPatterns;
    @Value("${lifs.auth.ldap.groupSearchBase}")
    private String ldapGroupSearchBase;
    @Value("${lifs.auth.ldap.groupSearchFilter}")
    private String ldapGroupSearchFilter;
    @Value("${lifs.auth.ldap.userPasswordAttribute}")
    private String ldapUserPasswordAttribute;

    @Autowired
    private CustomBasicAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.
                csrf()
                .ignoringAntMatchers("/rest/**")
                .csrfTokenRepository(new HttpSessionCsrfTokenRepository()).and().authorizeRequests()
                .antMatchers(
                        "/",
                        "/js/**",
                        "/css/**",
                        "/fonts/**",
                        "/img/**",
                        "/webjars/**",
                        "/health*",
                        "/login*",
                        "/actuator/health",
                        "/actuator/info").permitAll()
                .anyRequest().anonymous() //authenticated()
                .and().authorizeRequests().antMatchers("/user**").fullyAuthenticated()
                .and().httpBasic().authenticationEntryPoint(authenticationEntryPoint)
                .and()
                .formLogin().loginPage("/login").permitAll().and().logout()
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .permitAll();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LdapShaPasswordEncoder();
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .ldapAuthentication()
                .userDnPatterns(ldapUserDnPatterns)
                .groupSearchBase(ldapGroupSearchBase)
                .groupSearchFilter(ldapGroupSearchFilter)
                .contextSource()
                .url(ldapUrls + "/" + ldapBaseDn)
                .managerDn(ldapManagerDn).managerPassword(ldapManagerPassword)
                .and()
                .passwordCompare()
                .passwordEncoder(passwordEncoder())
                .passwordAttribute(ldapUserPasswordAttribute);
    }
}
