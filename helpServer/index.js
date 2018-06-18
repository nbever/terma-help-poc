// eslint-disable-next-line
const express = require('express');
// eslint-disable-next-line
const session = require('express-session');
const fs = require('fs');
const url = require('url');

console.log(process.argv);

const port = process.argv.length > 2 ? parseInt(process.argv[2]) : 8081;
const static_dir = process.argv.length > 3 ? process.argv[3] : './';

const app = express();
const licenseKeys = fs.readFileSync('licenses.txt').toString().split('\n');

const verifyLicense = (key) => {

  console.log('Verifying license key...');
  const keyIndex = licenseKeys.findIndex( (line) => {
    return key === line;
  });

  return keyIndex !== -1;
};

app.use(session({
  secret: 'SomeUniqueJawsKey',
  resave: true,
  saveUninitialized: false
}));

app.use('/', (request, response, next) => {

  if ( request.session.license !== undefined ){
    console.log('Session is established');
    next();
    return;
  }

  //validate the key
  const licenseToken = request.query.licenseKey;

  const verified = verifyLicense(licenseToken);

  if (verified !== true) {
    response.status(401).send('Unauthorized');
    return next('Unauthorized');
  }

  request.session.license = licenseToken;
  return next();
});

app.use(express.static(static_dir));

app.listen(
  port, 
  // eslint-disable-next-line
  () => console.log(`Terma WebHelp server listening on port ${port} serving ${static_dir}`));