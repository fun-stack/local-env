const path = require('path');
const url = require('url');
const express = require('express');
const {Provider} = require('oidc-provider');

const clientScope = 'email profile openid user/api';
const resourceUri = 'urn:fun:stack';

// https://github.com/panva/node-oidc-provider/tree/main/docs
function configuration() {
  return {
    claims: {
      "user/api": [],
      address: ['address'],
      email: ['email', 'email_verified'],
      phone: ['phone_number', 'phone_number_verified'],
      profile: ['birthdate', 'family_name', 'gender', 'given_name', 'locale', 'middle_name', 'name',
        'nickname', 'picture', 'preferred_username', 'profile', 'updated_at', 'website', 'zoneinfo', 'cognito:username', 'cognito:groups']
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
      redirect_uris: ['http://localhost'],
      post_logout_redirect_uris: ['http://localhost'],
      scope: clientScope
    }],

    conformIdTokenClaims: false,

    extraTokenClaims(ctx, token) {
      const id = token.accountId;
      const idSplit = id.split('+');
      const groups = idSplit.slice(1)
      const groupClaims = groups.length > 0 ? { "cognito:groups": groups } : {};

      return Object.assign({
        sub: id,
        username: id,
      }, groupClaims);
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
      const idSplit = id.split('+');
      const groups = idSplit.slice(1);
      const groupClaims = groups.length > 0 ? { "cognito:groups": groups } : {};

      return {
        accountId: id,
        async claims(use, scope) {
          return Object.assign({
            sub: id,
            "cognito:username": id,
            email: `${id}@localhost`,
            email_verified: true
          }, groupClaims);
        },
      };
    }
  };
}

function start(port) {
  const oidc = new Provider(`http://localhost:${port}`, configuration());

  // allow all redirect_uris
  oidc.Client.prototype.postLogoutRedirectUriAllowed = function() { return true; };
  oidc.Client.prototype.redirectUriAllowed = function() { return true; };

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
      } else if (req.method === 'GET' && (req.path === '/login' || req.path === '/signup' || req.path === '/authorize')) {
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
