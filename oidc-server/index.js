const path = require('path');
const url = require('url');
const express = require('express');
const {Provider} = require('oidc-provider');

const clientScope = 'email profile openid api';
const resourceUri = 'urn:fun:stack';

// https://github.com/panva/node-oidc-provider/tree/main/docs
function configuration() {
  return {
    claims: {
      api: [],
      address: ['address'],
      email: ['email', 'email_verified'],
      phone: ['phone_number', 'phone_number_verified'],
      profile: ['birthdate', 'family_name', 'gender', 'given_name', 'locale', 'middle_name', 'name',
        'nickname', 'picture', 'preferred_username', 'profile', 'updated_at', 'website', 'zoneinfo', 'cognito:username']
    },

    // copy cognito structure https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-userpools-server-contract-reference.html
    routes: {
      authorization: '/oauth2/authorize',
      backchannel_authentication: '/backchannel',
      code_verification: '/device',
      device_authorization: '/device/auth',
      end_session: '/logout',
      introspection: '/token/introspection',
      jwks: '/jwks',
      pushed_authorization_request: '/request',
      registration: '/reg',
      revocation: '/oauth2/revoke',
      token: '/oauth2/token',
      userinfo: '/oauth2/userInfo'
    },

    clients: [{
      application_type: 'web',
      response_types: ['code'],
      grant_types: ['authorization_code', 'refresh_token'],
      client_id: 'fun',
      client_secret: 'stack',
      token_endpoint_auth_method: 'none',
      redirect_uris: ['http://localhost:12345'],
      post_logout_redirect_uris: ['http://localhost:12345?logout'],
      scope: clientScope
    }],

    conformIdTokenClaims: false,

    extraTokenClaims(ctx, token) {
      return {
        "sub": token.accountId,
        "username": token.accountId,
        "cognito:groups": []
      }
    },

    rotateRefreshToken(ctx) {
      return false;
    },

    features: {
      resourceIndicators: {
        enabled: true,
        getResourceServerInfo: () => {
          return {
            scope: clientScope,
            audience: 'fun-api',
            accessTokenTTL: 60 * 60, // 2 hours
            accessTokenFormat: 'jwt'
          }
        },
        useGrantedResource: () => true
      }
    },

    pkce: {
      required(ctx, client) {
        return false;
      }
    },

    clientBasedCORS(ctx, origin, client) {
      return true;
    },

    async issueRefreshToken(ctx, client, code) {
      return true;
    },

    // https://github.com/panva/node-oidc-provider/blob/main/recipes/skip_consent.md
    async loadExistingGrant(ctx) {
      const grantId = (ctx.oidc.result && ctx.oidc.result.consent && ctx.oidc.result.consent.grantId) || ctx.oidc.session.grantIdFor(ctx.oidc.client.clientId);

      if (grantId) {
        const grant = await ctx.oidc.provider.Grant.find(grantId);
        if (ctx.oidc.account && grant.exp < ctx.oidc.session.exp) {
          grant.exp = ctx.oidc.session.exp;
          await grant.save();
        }

        return grant;
      } else {
        const grant = new ctx.oidc.provider.Grant({
          clientId: ctx.oidc.client.clientId,
          accountId: ctx.oidc.account.accountId,
        });

        grant.addOIDCScope(clientScope);
        grant.addResourceScope(resourceUri, clientScope);

        await grant.save();
        return grant;
      }
    },

    async findAccount(ctx, id) {
      return {
        accountId: id,
        // account: id,
        async claims(use, scope) {
          return {
            sub: id,
            "cognito:username": id,
            email: `${id}@localhost`,
            email_verified: true
          };
        },
      };
    }
  };
}

function start(port) {
  const oidc = new Provider(`http://localhost:${port}`, configuration());

  const app = express();

  let server;
  (async () => {
    app.use((req, res, next) => {
      // make compatible with cognito
      if (req.method === 'GET' && (req.path === '/oauth2/authorize' && (!(req.query || {}).resource || !(req.query || {}).scope))) {
        const newUrl = url.format({
          protocol: 'http',
          host: req.get('host'),
          pathname: '/oauth2/authorize',
          query: Object.assign({scope: clientScope, resource: resourceUri}, req.query || {})
        });
        res.redirect(newUrl);
      } else if (req.method === 'GET' && (req.path === '/login' || req.path === '/signup')) {
        //TODO: signup?
        const newUrl = url.format({
          protocol: 'http',
          host: req.get('host'),
          pathname: '/oauth2/authorize',
          query: req.query
        });
        res.redirect(newUrl);
      } else if (req.method === 'GET' && req.path === '/logout' && (req.query || {}).logout_uri) {
        const logout_uri = req.query.logout_uri;
        delete req.query.logout_uri;
        const newUrl = url.format({
          protocol: 'http',
          host: req.get('host'),
          pathname: '/logout',
          query: Object.assign({post_logout_redirect_uri: logout_uri}, req.query)
        });
        res.redirect(newUrl);
      } else {
        next();
      }
    });

    app.use('/', oidc.callback());

    server = app.listen(port, () => {
      // console.log(`application is listening on port ${port}, check its /.well-known/openid-configuration`);
    });
  })().catch((err) => {
    if (server && server.listening) server.close();
    console.error(err);
    process.exitCode = 1;
  });
}

// start(8082);
module.exports = {start}
