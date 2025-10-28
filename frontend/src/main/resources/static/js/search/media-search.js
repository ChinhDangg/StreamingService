const searchForm = document.querySelector('#search-form');
const searchInput = document.querySelector('#search-input');


searchForm.addEventListener('submit', (e) => {
   e.preventDefault();
   console.log(searchInput.value);
});