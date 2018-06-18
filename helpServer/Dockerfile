FROM node:8.11.1

ENV PORT=8083
ENV PUBLIC_CONTENT=/opt/termahelp

COPY index.js /help-server/
COPY package.json /help-server/
COPY licenses.txt /help-server/

WORKDIR /help-server

RUN npm install 

EXPOSE 8083
CMD node ./index.js ${PORT} ${PUBLIC_CONTENT}