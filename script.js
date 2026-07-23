const ano = document.getElementById('ano');
if (ano) {
  ano.textContent = new Date().getFullYear();
}

const menuButton = document.querySelector('.menu-toggle');
const navLinks = document.querySelector('.nav-links');

if (menuButton && navLinks) {
  menuButton.addEventListener('click', () => {
    navLinks.classList.toggle('open');
  });
}
