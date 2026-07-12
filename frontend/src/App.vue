<template>
  <div class="app">
    <router-view />
  </div>
</template>

<script setup>
// App 组件
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app,
.app {
  width: 100%;
  height: 100%;
  overflow: hidden;
  margin: 0;
  padding: 0;
}

body {
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue",
    Arial, sans-serif;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
}

.app {
  display: flex;
  flex-direction: column;
}
</style>
