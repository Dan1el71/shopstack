FROM node:20-alpine as build

WORKDIR /shopstack-ui/

RUN apk update && \
    apk add --no-cache python3 make g++

COPY package.json package-lock.json /shopstack-ui/
RUN npm install

COPY . /shopstack-ui/

FROM node:20-alpine

WORKDIR /shopstack-ui/

COPY --from=build /shopstack-ui/ /shopstack-ui/

EXPOSE 8080

CMD ["npm", "start", "--", "--host", "0.0.0.0", "--port", "8080"]
