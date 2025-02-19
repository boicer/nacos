/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LDAP auth provider.
 *
 * @author zjw
 */
@Component
public class LdapAuthenticationProvider implements AuthenticationProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationProvider.class);
    
    private static final String DEFAULT_PASSWORD = "nacos";
    
    private static final String LDAP_PREFIX = "LDAP_";
    
    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private NacosRoleServiceImpl nacosRoleService;
    
    @Lazy
    @Autowired
    private LdapTemplate ldapTemplate;
    
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = (String) authentication.getPrincipal();
        String password = (String) authentication.getCredentials();
        
        if (isAdmin(username)) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (PasswordEncoderUtil.matches(password, userDetails.getPassword())) {
                return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
            } else {
                return null;
            }
        }
        
        if (!ldapLogin(username, password)) {
            return null;
        }
        
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(LDAP_PREFIX + username);
        } catch (UsernameNotFoundException exception) {
            String nacosPassword = PasswordEncoderUtil.encode(DEFAULT_PASSWORD);
            userDetailsService.createUser(LDAP_PREFIX + username, nacosPassword);
            User user = new User();
            user.setUsername(LDAP_PREFIX + username);
            user.setPassword(nacosPassword);
            userDetails = new NacosUserDetails(user);
        }
        return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
    }
    
    private boolean isAdmin(String username) {
        List<RoleInfo> roleInfos = nacosRoleService.getRoles(username);
        if (CollectionUtils.isEmpty(roleInfos)) {
            return false;
        }
        for (RoleInfo roleinfo : roleInfos) {
            if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleinfo.getRole())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean ldapLogin(String username, String password) throws AuthenticationException {
        return ldapTemplate.authenticate("", "(uid=" + username + ")", password);
    }
    
    @Override
    public boolean supports(Class<?> aClass) {
        return aClass.equals(UsernamePasswordAuthenticationToken.class);
    }
    
}
