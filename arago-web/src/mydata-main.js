import { mount } from 'svelte';
import './app.css';
import MyData from './MyData.svelte';

const app = mount(MyData, { target: document.getElementById('mydata') });

export default app;
