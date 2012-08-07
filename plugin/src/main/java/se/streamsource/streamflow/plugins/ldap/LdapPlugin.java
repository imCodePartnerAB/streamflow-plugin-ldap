/**
 *
 * Copyright 2009-2012 Jayway Products AB
 *
 * License statement goes here
 */
package se.streamsource.streamflow.plugins.ldap;

import org.qi4j.api.configuration.Configuration;
import org.qi4j.api.injection.scope.Structure;
import org.qi4j.api.injection.scope.This;
import org.qi4j.api.mixin.Mixins;
import org.qi4j.api.service.Activatable;
import org.qi4j.api.service.ServiceComposite;
import org.qi4j.api.structure.Module;
import org.qi4j.api.value.ValueBuilder;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.streamsource.streamflow.server.plugin.authentication.Authenticator;
import se.streamsource.streamflow.server.plugin.authentication.UserDetailsValue;
import se.streamsource.streamflow.server.plugin.authentication.UserIdentityValue;
import se.streamsource.streamflow.server.plugin.ldapimport.GroupDetailsValue;
import se.streamsource.streamflow.server.plugin.ldapimport.GroupListValue;
import se.streamsource.streamflow.server.plugin.ldapimport.LdapImporter;
import se.streamsource.streamflow.server.plugin.ldapimport.UserListValue;
import se.streamsource.streamflow.util.Strings;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: arvidhuss
 * Date: 8/6/12
 * Time: 7:44 AM
 * To change this template use File | Settings | File Templates.
 */
@Mixins(LdapPlugin.Mixin.class)
public interface LdapPlugin extends ServiceComposite, Activatable, Authenticator, LdapImporter,
      Configuration
{

   abstract class Mixin implements LdapPlugin
   {
      protected static final Logger logger = LoggerFactory.getLogger( LdapPlugin.class );

      @Structure
      protected Module module;

      @This
      protected Configuration<LdapPluginConfiguration> config;

      protected DirContext ctx;

      public void passivate() throws Exception
      {
         if( ctx != null )
         {
            ctx.close();
            ctx = null;
         }
      }

      public void activate() throws Exception
      {
         if ( !LdapPluginConfiguration.Name.not_configured.name()
               .equals(  config.configuration().name().get() ) && checkConfigOk() )
         {
            createInitialContext();
         }
      }

      private void createInitialContext()
      {
         Hashtable<String, String> env = new Hashtable<String, String>();
         env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
         env.put(Context.PROVIDER_URL, config.configuration().url().get());
         env.put(Context.SECURITY_AUTHENTICATION, "simple");

         if (!config.configuration().username().get().isEmpty())
         {
            env.put( Context.SECURITY_PRINCIPAL, config.configuration().username().get());
            env.put(Context.SECURITY_CREDENTIALS, config.configuration().password().get());
         }

         try
         {
            ctx = new InitialDirContext(env);

            logger.info( "Established connection with LDAP server at " + config.configuration().url().get() );

         } catch (AuthenticationException ae)
         {
            logger.warn("Could not log on ldap-server with service account");
            throw new ResourceException( Status.SERVER_ERROR_INTERNAL, ae);
         } catch (NamingException e)
         {
            logger.warn("Problem establishing connection with ldap-server", e);
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
         }
      }

      private void resetSecurityCredentials()
            throws NamingException
      {
         ctx.removeFromEnvironment( Context.SECURITY_PRINCIPAL );
         ctx.removeFromEnvironment( Context.SECURITY_CREDENTIALS );
         if(!config.configuration().username().get().isEmpty() )
         {
            ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, config.configuration().username().get());
            ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, config.configuration().password().get());
         }
      }

      protected boolean checkConfigOk()
      {
         LdapPluginConfiguration.Name name = LdapPluginConfiguration.Name.valueOf( config.configuration().name().get() );
         if ((LdapPluginConfiguration.Name.ad != name
               && LdapPluginConfiguration.Name.edirectory != name && LdapPluginConfiguration.Name.apacheds != name)
               || Strings.empty( config.configuration().nameAttribute().get() )
               || Strings.empty(config.configuration().phoneAttribute().get())
               || Strings.empty(config.configuration().emailAttribute().get())
               || Strings.empty(config.configuration().userSearchbase().get())
               || Strings.empty(config.configuration().groupSearchbase().get()))
         {
            return false;
         }
         return true;
      }

      protected String createFilterForUidQuery()
      {
         switch (LdapPluginConfiguration.Name.valueOf( config.configuration().name().get() ) )
         {
            case ad:
               return "(&(objectclass=person)(uid={0}))";
            case edirectory:
            case apacheds:
               return "(&(objectClass=inetOrgPerson)(uid={0}))";
            default:
               return null;
         }
      }

      protected String createFilterForUidsQuery()
      {
         switch (LdapPluginConfiguration.Name.valueOf( config.configuration().name().get() ) )
         {
            case ad:
               return "(objectclass=person)";
            case edirectory:
            case apacheds:
               return "(objectClass=inetOrgPerson)";
            default:
               return null;
         }
      }

      protected String createFilterForGroupQuery()
      {
         switch (LdapPluginConfiguration.Name.valueOf( config.configuration().name().get() ) )
         {
            case ad:
            case edirectory:
               return "(&(member={0})(objectClass=groupOfNames))";
            case apacheds:
               return "(&(uniqueMember={0})(objectClass=groupOfUniqueNames))";
            default:
               return null;
         }
      }

      protected String createFilterForGroupsQuery()
      {
         switch (LdapPluginConfiguration.Name.valueOf( config.configuration().name().get() ) )
         {
            case ad:
            case edirectory:
               return "(objectClass=groupOfNames)";
            case apacheds:
               return "(objectClass=groupOfUniqueNames)";
            default:
               return null;
         }
      }

      protected String[] createReturnAttributesForGroupQuery()
      {
         switch (LdapPluginConfiguration.Name.valueOf( config.configuration().name().get() ) )
         {
            case ad:
            case edirectory:
               return new String[] { "member" };
            case apacheds:
               return new String[] { "cn", "uniqueMember", "entryUUID" };
            default:
               return new String[0];
         }
      }

      protected String[] createReturnAttributesForUidQuery()
      {
         return new String[]
               { "uid", "cn", config.configuration().nameAttribute().get(), config.configuration().emailAttribute().get(),
                     config.configuration().phoneAttribute().get() };
      }

      public void authenticate(UserIdentityValue user)
      {
         userdetails(user);
      }

      public UserDetailsValue userdetails(UserIdentityValue user)
      {
         String uid = user.username().get();
         String password = user.password().get();

         if( checkConfigOk() )
         {
            return lookupUserDetails( uid, password);
         } else
         {
            logger.error("Plugin is not reasonably configured!");
            throw new ResourceException(Status.CLIENT_ERROR_PRECONDITION_FAILED);
         }
      }

      private UserDetailsValue lookupUserDetails( String uid, String password)
      {
         try
         {
            resetSecurityCredentials();

            String filter = createFilterForUidQuery();

            SearchControls ctls = new SearchControls();
            ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctls.setReturningAttributes( createReturnAttributesForUidQuery() );
            ctls.setReturningObjFlag(true);

            NamingEnumeration<SearchResult> enm = ctx.search(config.configuration().userSearchbase().get(), filter,
                  new String[]
                        { uid }, ctls);

            UserDetailsValue userDetails = null;
            String dn = null;

            if (enm.hasMore())
            {
               SearchResult result = (SearchResult) enm.next();
               dn = result.getNameInNamespace();
               userDetails = createUserDetails(result, uid);
            }

            if (dn == null || enm.hasMore())
            {
               throw new ResourceException(Status.CLIENT_ERROR_UNAUTHORIZED);
            }

            ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
            ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
            // Perform a lookup in order to force a bind operation with JNDI
            ctx.lookup(dn);

            logger.debug("Authentication successful for user: " + dn);

            return userDetails;

         } catch (AuthenticationException ae)
         {
            logger.debug("User could not be authenticated:", ae);
            throw new ResourceException(Status.CLIENT_ERROR_UNAUTHORIZED, ae);

         } catch (NamingException e)
         {
            logger.debug("Unknown error while authenticating user: ", e);
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
         }
      }

      private UserDetailsValue createUserDetails(SearchResult result, String username) throws NamingException
      {
         ValueBuilder<UserDetailsValue> builder = module.valueBuilderFactory().newValueBuilder(UserDetailsValue.class);

         Attribute nameAttribute = result.getAttributes().get(config.configuration().nameAttribute().get());
         Attribute emailAttribute = result.getAttributes().get(config.configuration().emailAttribute().get());
         Attribute phoneAttribute = result.getAttributes().get(config.configuration().phoneAttribute().get());
         Attribute uid  = result.getAttributes().get( "uid" );

         if (nameAttribute != null)
         {
            builder.prototype().name().set((String) nameAttribute.get());
         }

         if (emailAttribute != null)
         {
            builder.prototype().emailAddress().set((String) emailAttribute.get());
         }

         if (phoneAttribute != null)
         {
            builder.prototype().phoneNumber().set((String) phoneAttribute.get());
         }

         if( uid != null )
         {
            builder.prototype().username().set((String)uid.get() );
         } else
         {
            builder.prototype().username().set( username );
         }

         return builder.newInstance();
      }

      public GroupListValue importgroups()
      {
         ValueBuilder<GroupListValue> listBuilder = module.valueBuilderFactory().newValueBuilder( GroupListValue.class );
         try
         {
            resetSecurityCredentials();

            SearchControls groupCtls = new SearchControls();
            groupCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            String[] returningAttributes = createReturnAttributesForGroupQuery();
            String filter = createFilterForGroupsQuery();

            groupCtls.setReturningAttributes(returningAttributes);
            groupCtls.setReturningObjFlag(true);
            NamingEnumeration<SearchResult> groups = ctx.search(config.configuration().groupSearchbase().get(), filter,
                  new String[0], groupCtls);

            List<GroupDetailsValue> groupsList = new ArrayList<GroupDetailsValue>( );
            ValueBuilder<GroupDetailsValue> groupBuilder = module.valueBuilderFactory().newValueBuilder( GroupDetailsValue.class );
            while( groups.hasMore() )
            {
               SearchResult searchResult = groups.next();

               Attribute id = searchResult.getAttributes().get( "entryUUID" );
               groupBuilder.prototype().id().set( (String)id.get() );

               Attribute name = searchResult.getAttributes().get( "cn" );
               groupBuilder.prototype().name().set( (String)name.get() );

               List<String> memberIds = new ArrayList<String>(  );
               Attribute members = searchResult.getAttributes().get( "uniqueMember" );
               for( int i=0; i<members.size(); i++ )
               {
                  LdapName ldapName = new LdapName( (String)members.get( i ) );
                  for(Rdn rdn : ldapName.getRdns() )
                  {
                     if("uid".equals( rdn.getType() ))
                        memberIds.add(  (String)rdn.getValue() );
                  }

               }

               groupBuilder.prototype().members().set( memberIds );
               groupsList.add( groupBuilder.newInstance() );
            }

            listBuilder.prototype().groups().set( groupsList );

         } catch (NamingException ne )
         {
            logger.debug("Unknown error while importing groups: ", ne);
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, ne);
         }

         return listBuilder.newInstance();
      }

      public UserListValue importusers()
      {
         ValueBuilder<UserListValue> listBuilder = module.valueBuilderFactory().newValueBuilder( UserListValue.class );

         try
         {
            resetSecurityCredentials();

            SearchControls userCtls = new SearchControls();
            userCtls.setSearchScope( SearchControls.SUBTREE_SCOPE );

            String filter = createFilterForUidsQuery();

            userCtls.setReturningAttributes( createReturnAttributesForUidQuery() );
            userCtls.setReturningObjFlag( true );
            NamingEnumeration<SearchResult> users = ctx.search(config.configuration().userSearchbase().get(), filter,
                  new String[0], userCtls);

            List<UserDetailsValue> userList = new ArrayList<UserDetailsValue>( );
            while( users.hasMore() )
            {
               SearchResult searchResult = users.next();
               userList.add( createUserDetails( searchResult, null ) );
            }

         listBuilder.prototype().users().set( userList );

      } catch (NamingException ne )
      {
         logger.debug("Unknown error while importing users: ", ne);
         throw new ResourceException(Status.SERVER_ERROR_INTERNAL, ne);
      }

      return listBuilder.newInstance();
      }
   }
}